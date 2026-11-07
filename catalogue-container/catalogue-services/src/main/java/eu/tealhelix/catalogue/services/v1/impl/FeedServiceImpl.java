package eu.tealhelix.catalogue.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;
import static eu.tealhelix.common.utils.UniComprehensions.forcm;

import java.io.InputStream;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.dao.CatalogueDao;
import eu.tealhelix.catalogue.dao.FeedDao;
import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.services.v1.FeedService;
import eu.tealhelix.catalogue.services.v1.types.FeedWithRuns;
import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.model.Feed;
import eu.tealhelix.catalogue.v1.model.ImmutableFeed;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRun;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.HasCatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.HasFeedId;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.impl.FeedIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class FeedServiceImpl implements FeedService {
	private final TealHelixAuthorization authorization;
	private final CatalogueDao catalogueDao;
	private final FeedDao feedDao;
	private final IngestionRunDao ingestionRunDao;
	private final FeedStore feedStore;
	private final DateTimeService dateTimeService;
	private final UniAwaiter uniAwaiter;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final IngestionRunDispatcher dispatcher;

	@Inject
	public FeedServiceImpl(
			TealHelixAuthorization authorization,
			CatalogueDao catalogueDao,
			FeedDao feedDao,
			IngestionRunDao ingestionRunDao,
			FeedStore feedStore,
			DateTimeService dateTimeService,
			UniAwaiter uniAwaiter,
			ReactivePersistenceContextFactory persistenceContextFactory,
			IngestionRunDispatcher dispatcher
	) {
		this.authorization = authorization;
		this.catalogueDao = catalogueDao;
		this.feedDao = feedDao;
		this.ingestionRunDao = ingestionRunDao;
		this.feedStore = feedStore;
		this.dateTimeService = dateTimeService;
		this.uniAwaiter = uniAwaiter;
		this.persistenceContextFactory = persistenceContextFactory;
		this.dispatcher = dispatcher;
	}

	/**
	 * The caller and the Catalogue are checked before the body is read, so a refused Feed costs nothing to store.
	 */
	@Override
	public FeedWithRuns receive(User caller, HasCatalogueId catalogueId, FeedType feedType, InputStream body) {
		authorization.requireRetailer(caller);
		RequiredInputMissingException.throwIfRequiredInputMissing("type", feedType);
		var catalogue = uniAwaiter.await(() -> persistenceContextFactory.withoutTransaction(em -> catalogueDao.find(em, catalogueId)));
		requireAcceptsFeedsOf(catalogue, catalogueId, caller);
		return store(catalogue.getRetailerId(), List.of(catalogue), feedType, body);
	}

	/**
	 * The Catalogues are read before the body is, so a Feed that no Catalogue takes costs nothing to store.
	 */
	@Override
	public FeedWithRuns receiveForActiveCatalogues(User caller, FeedType feedType, InputStream body) {
		authorization.requireRetailer(caller);
		RequiredInputMissingException.throwIfRequiredInputMissing("type", feedType);
		var retailerId = new GenericRetailerId(caller.getId().asString());
		var catalogues = uniAwaiter.await(() -> persistenceContextFactory.withoutTransaction(em -> catalogueDao.findActive(em, retailerId)));
		if (catalogues.isEmpty()) {
			throw new NotAuthorizedException("The Retailer has no active Catalogue, retailerId: " + retailerId.asString());
		}
		return store(retailerId, catalogues, feedType, body);
	}

	@Override
	public Uni<FeedWithRuns> find(User caller, HasFeedId feedId) {
		authorization.requireLogin(caller);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				requireReadableFeed(em, caller, feedId),
				feed -> ingestionRunDao.findOfFeed(em, feed),
				FeedWithRuns::new
		));
	}

	/**
	 * Store a Feed once and record it with one run per Catalogue. The Feed and its runs are recorded after the Feed is
	 * stored whole and in one transaction, so a run never points to a Feed that is not there; if they cannot be recorded,
	 * the Feed is removed again. The runs are processed after the call returns. The time of the receipt is kept to the
	 * microsecond, as the database keeps it, so that the Feed answered and the Feed read back agree.
	 */
	private FeedWithRuns store(RetailerId retailerId, List<Catalogue> catalogues, FeedType feedType, InputStream body) {
		var feedId = new FeedIdImpl(UUID.randomUUID().toString());
		var storedFeed = feedStore.store(retailerId, feedId, body);
		var feed = ImmutableFeed.builder()
				.id(feedId)
				.retailerId(retailerId)
				.feedType(feedType)
				.receivedAt(dateTimeService.getNow().truncatedTo(ChronoUnit.MICROS))
				.filePath(storedFeed.path())
				.fileSize(storedFeed.size())
				.fileSha256(storedFeed.sha256())
				.build();
		List<IngestionRun> runs = catalogues.stream()
				.<IngestionRun>map(catalogue -> ImmutableIngestionRun.builder()
						.id(new IngestionRunIdImpl(UUID.randomUUID().toString()))
						.feed(feed)
						.catalogueId(catalogue.getId())
						.status(IngestionRunStatus.RECEIVED)
						.build())
				.toList();
		try {
			uniAwaiter.await(() -> persistenceContextFactory.withTransaction(tx -> forc(
					feedDao.create(tx, feed),
					() -> ingestionRunDao.create(tx, runs)
			)));
		} catch (RuntimeException e) {
			feedStore.delete(storedFeed.path());
			throw e;
		}
		runs.forEach(run -> dispatcher.wake(run.getCatalogueId()));
		return new FeedWithRuns(feed, runs);
	}

	private static void requireAcceptsFeedsOf(Catalogue catalogue, HasCatalogueId catalogueId, User caller) {
		if (catalogue == null) {
			throw new NotFoundException(catalogueId.getId(), "No such Catalogue, catalogueId: " + catalogueId.getId().asString());
		} else if (!catalogue.getRetailerId().asUuid().equals(caller.getId().asUuid())) {
			throw new NotAuthorizedException("The Catalogue belongs to another Retailer, catalogueId: " + catalogueId.getId().asString());
		} else if (!catalogue.isActive()) {
			throw new NotAuthorizedException("The Catalogue does not accept Feeds, catalogueId: " + catalogueId.getId().asString());
		}
	}

	/**
	 * An operator reads every Feed; a Retailer reads the Feeds it sent, and so the runs of its own Catalogues.
	 */
	private Uni<Feed> requireReadableFeed(ReactivePersistenceContext em, User caller, HasFeedId feedId) {
		return feedDao.find(em, feedId).flatMap(feed -> {
			if (feed == null) {
				return Uni.createFrom().failure(new NotFoundException(feedId.getId(), "No such Feed, feedId: " + feedId.getId().asString()));
			} else if (caller.isOperator()) {
				return Uni.createFrom().item(feed);
			} else if (!caller.isRetailer()) {
				return Uni.createFrom().failure(new NotAuthorizedException("Only Retailers and operators read Feeds"));
			} else if (!feed.getRetailerId().asUuid().equals(caller.getId().asUuid())) {
				return Uni.createFrom().failure(new NotAuthorizedException("The Feed belongs to another Retailer, feedId: " + feedId.getId().asString()));
			}
			return Uni.createFrom().item(feed);
		});
	}
}
