package eu.tealhelix.catalogue.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.dao.CatalogueDao;
import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.dao.RetailerProductDao;
import eu.tealhelix.catalogue.services.v1.IngestionRunService;
import eu.tealhelix.catalogue.services.v1.types.IngestionRunNotHeldException;
import eu.tealhelix.catalogue.services.v1.types.IngestionRunNotReplayableException;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRun;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRunCounts;
import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.HasIngestionRunId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.v1.model.User;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class IngestionRunServiceImpl implements IngestionRunService {
	private static final Set<IngestionRunStatus> REPLAYABLE = EnumSet.of(IngestionRunStatus.DONE, IngestionRunStatus.FAILED, IngestionRunStatus.DISCARDED);

	private final TealHelixAuthorization authorization;
	private final CatalogueDao catalogueDao;
	private final IngestionRunDao ingestionRunDao;
	private final RetailerProductDao retailerProductDao;
	private final DateTimeService dateTimeService;
	private final IngestionRunDispatcher dispatcher;
	private final FeedStore feedStore;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	@Inject
	public IngestionRunServiceImpl(
			TealHelixAuthorization authorization,
			CatalogueDao catalogueDao,
			IngestionRunDao ingestionRunDao,
			RetailerProductDao retailerProductDao,
			DateTimeService dateTimeService,
			IngestionRunDispatcher dispatcher,
			FeedStore feedStore,
			ReactivePersistenceContextFactory persistenceContextFactory
	) {
		this.authorization = authorization;
		this.catalogueDao = catalogueDao;
		this.ingestionRunDao = ingestionRunDao;
		this.retailerProductDao = retailerProductDao;
		this.dateTimeService = dateTimeService;
		this.dispatcher = dispatcher;
		this.feedStore = feedStore;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<IngestionRun> find(User caller, HasIngestionRunId runId) {
		authorization.requireLogin(caller);
		return persistenceContextFactory.withoutTransaction(em -> requireReadableRun(em, caller, runId));
	}

	@Override
	public Uni<List<IngestionReject>> findRejects(User caller, HasIngestionRunId runId, int offset, int limit) {
		authorization.requireLogin(caller);
		requirePage(offset, limit);
		return persistenceContextFactory.withoutTransaction(em -> forc(
				requireReadableRun(em, caller, runId),
				run -> ingestionRunDao.findRejects(em, runId, offset, limit)
		));
	}

	/**
	 * The deactivation and the end of the run commit together, so that the Listings are taken off sale if and only if
	 * the run is confirmed.
	 */
	@Override
	public Uni<IngestionRun> confirm(User caller, HasIngestionRunId runId) {
		authorization.requireOperator(caller);
		return decide(runId, (tx, held) -> forc(
				retailerProductDao.deactivateUnseenListings(tx, held.getCatalogueId(), runId),
				deactivated -> storeDecision(tx, ImmutableIngestionRun.builder()
						.from(held)
						.status(IngestionRunStatus.DONE)
						.finishedAt(dateTimeService.getNow())
						.counts(ImmutableIngestionRunCounts.copyOf(held.getCounts()).withDeactivated(deactivated))
						.build())
		));
	}

	@Override
	public Uni<IngestionRun> discard(User caller, HasIngestionRunId runId) {
		authorization.requireOperator(caller);
		return decide(runId, (tx, held) -> storeDecision(tx, ImmutableIngestionRun.builder()
				.from(held)
				.status(IngestionRunStatus.DISCARDED)
				.finishedAt(dateTimeService.getNow())
				.build()));
	}

	/**
	 * The new run is processed after it is committed, so that the processing finds it.
	 */
	@Override
	public Uni<IngestionRun> replay(User caller, HasIngestionRunId runId) {
		authorization.requireOperator(caller);
		return persistenceContextFactory.withTransaction(tx -> forc(
						ingestionRunDao.find(tx, runId),
						run -> requireReplayable(tx, runId, run),
						replayed -> {
							IngestionRun replay = ImmutableIngestionRun.builder()
									.id(new IngestionRunIdImpl(UUID.randomUUID().toString()))
									.feed(replayed.getFeed())
									.catalogueId(replayed.getCatalogueId())
									.status(IngestionRunStatus.RECEIVED)
									.replayOfRunId(runId.getId())
									.build();
							return ingestionRunDao.create(tx, List.of(replay)).replaceWith(replay);
						}
				))
				.invoke(replay -> dispatcher.wake(replay.getCatalogueId()));
	}

	/**
	 * Decide on a held run in one transaction, and then have the runs of its Catalogue that waited for it processed.
	 *
	 * @param decision Given the held run, ends it
	 */
	private Uni<IngestionRun> decide(HasIngestionRunId runId, BiFunction<ReactivePersistenceTxContext, IngestionRun, Uni<IngestionRun>> decision) {
		return persistenceContextFactory.withTransaction(tx -> forc(
						ingestionRunDao.find(tx, runId),
						run -> {
							if (run == null) {
								return Uni.createFrom().failure(new NotFoundException(runId.getId(), "No such Ingestion Run, runId: " + runId.getId().asString()));
							} else if (run.getStatus() != IngestionRunStatus.HELD) {
								return Uni.createFrom().failure(new IngestionRunNotHeldException(runId, run.getStatus()));
							}
							return decision.apply(tx, run);
						}
				))
				.invoke(decided -> dispatcher.wake(decided.getCatalogueId()));
	}

	/**
	 * Of two operators deciding on the same run at once, the second finds it decided already.
	 */
	private Uni<IngestionRun> storeDecision(ReactivePersistenceTxContext tx, IngestionRun decided) {
		return ingestionRunDao.updateHeld(tx, decided).flatMap(stored -> stored
				? Uni.createFrom().item(decided)
				: Uni.createFrom().failure(new IngestionRunNotHeldException(decided)));
	}

	/**
	 * Looking for the Feed in the store blocks for as long as a lookup of a local file takes.
	 */
	private Uni<IngestionRun> requireReplayable(ReactivePersistenceContext em, HasIngestionRunId runId, IngestionRun run) {
		if (run == null) {
			return Uni.createFrom().failure(new NotFoundException(runId.getId(), "No such Ingestion Run, runId: " + runId.getId().asString()));
		} else if (!REPLAYABLE.contains(run.getStatus())) {
			return Uni.createFrom().failure(IngestionRunNotReplayableException.unfinished(runId, run.getStatus()));
		}
		var superseded = run.getFeed().getFeedType() == FeedType.SNAPSHOT
				? ingestionRunDao.hasSnapshotReceivedAfter(em, run.getCatalogueId(), run.getFeed().getReceivedAt())
				: Uni.createFrom().item(false);
		return superseded.flatMap(later -> {
			if (later) {
				return Uni.createFrom().failure(IngestionRunNotReplayableException.superseded(runId));
			} else if (!feedStore.exists(run.getFeed().getFilePath())) {
				return Uni.createFrom().failure(new NotFoundException(runId.getId(), "The Feed of the Ingestion Run is no longer stored, runId: " + runId.getId().asString()));
			}
			return Uni.createFrom().item(run);
		});
	}

	private static void requirePage(int offset, int limit) {
		if (offset < 0) {
			throw BadInputValueException.fromInputNameAndHint("offset", "It must not be negative");
		}
		if (limit < 1 || limit > MAX_REJECTS_PER_PAGE) {
			throw BadInputValueException.fromInputNameAndHint("limit", "It must be from 1 to " + MAX_REJECTS_PER_PAGE);
		}
	}

	/**
	 * An operator reads every run; a Retailer reads the runs of its own Catalogues.
	 */
	private Uni<IngestionRun> requireReadableRun(ReactivePersistenceContext em, User caller, HasIngestionRunId runId) {
		return ingestionRunDao.find(em, runId).flatMap(run -> {
			if (run == null) {
				return Uni.createFrom().failure(new NotFoundException(runId.getId(), "No such Ingestion Run, runId: " + runId.getId().asString()));
			} else if (caller.isOperator()) {
				return Uni.createFrom().item(run);
			} else if (!caller.isRetailer()) {
				return Uni.createFrom().failure(new NotAuthorizedException("Only Retailers and operators read Ingestion Runs"));
			} else {
				return catalogueDao.find(em, run.getCatalogueId()).flatMap(catalogue ->
						catalogue.getRetailerId().asUuid().equals(caller.getId().asUuid())
								? Uni.createFrom().item(run)
								: Uni.createFrom().failure(new NotAuthorizedException("The Ingestion Run belongs to another Retailer, runId: " + runId.getId().asString())));
			}
		});
	}
}
