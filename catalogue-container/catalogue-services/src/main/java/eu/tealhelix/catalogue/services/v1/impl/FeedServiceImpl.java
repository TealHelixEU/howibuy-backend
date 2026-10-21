package eu.tealhelix.catalogue.services.v1.impl;

import java.io.InputStream;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.dao.CatalogueDao;
import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.services.v1.FeedService;
import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRun;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.HasCatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.v1.model.User;

@ApplicationScoped
public class FeedServiceImpl implements FeedService {
	private final TealHelixAuthorization authorization;
	private final CatalogueDao catalogueDao;
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
			IngestionRunDao ingestionRunDao,
			FeedStore feedStore,
			DateTimeService dateTimeService,
			UniAwaiter uniAwaiter,
			ReactivePersistenceContextFactory persistenceContextFactory,
			IngestionRunDispatcher dispatcher
	) {
		this.authorization = authorization;
		this.catalogueDao = catalogueDao;
		this.ingestionRunDao = ingestionRunDao;
		this.feedStore = feedStore;
		this.dateTimeService = dateTimeService;
		this.uniAwaiter = uniAwaiter;
		this.persistenceContextFactory = persistenceContextFactory;
		this.dispatcher = dispatcher;
	}

	/**
	 * The caller and the Catalogue are checked before the body is read, so a refused Feed costs nothing to store. The run
	 * is recorded after the Feed is stored whole, so a run never points to a Feed that is not there; if the run cannot be
	 * recorded, the Feed is removed again. The run is processed after the call returns.
	 */
	@Override
	public IngestionRun receive(User caller, HasCatalogueId catalogueId, FeedType feedType, InputStream body) {
		authorization.requireRetailer(caller);
		var catalogue = uniAwaiter.await(() -> persistenceContextFactory.withoutTransaction(em -> catalogueDao.find(em, catalogueId)));
		requireAcceptsFeedsOf(catalogue, catalogueId, caller);
		var runId = new IngestionRunIdImpl(UUID.randomUUID().toString());
		var storedFeed = feedStore.store(catalogueId.getId(), runId, body);
		var run = ImmutableIngestionRun.builder()
				.id(runId)
				.catalogueId(catalogueId.getId())
				.feedType(feedType)
				.status(IngestionRunStatus.RECEIVED)
				.receivedAt(dateTimeService.getNow())
				.filePath(storedFeed.path())
				.fileSize(storedFeed.size())
				.fileSha256(storedFeed.sha256())
				.build();
		try {
			uniAwaiter.await(() -> persistenceContextFactory.withTransaction(tx -> ingestionRunDao.create(tx, run)));
		} catch (RuntimeException e) {
			feedStore.delete(storedFeed.path());
			throw e;
		}
		dispatcher.wake(catalogueId.getId());
		return run;
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
}
