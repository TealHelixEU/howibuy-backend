package eu.tealhelix.catalogue.dao.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Root;

import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.dao.jpa.FeedEntity;
import eu.tealhelix.catalogue.dao.jpa.FeedEntity_;
import eu.tealhelix.catalogue.dao.jpa.IngestionRejectEntity;
import eu.tealhelix.catalogue.dao.jpa.IngestionRejectEntity_;
import eu.tealhelix.catalogue.dao.jpa.IngestionRunEntity;
import eu.tealhelix.catalogue.dao.jpa.IngestionRunEntity_;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionReject;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRun;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRunCounts;
import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.model.IngestionRunCounts;
import eu.tealhelix.catalogue.v1.types.HasCatalogueId;
import eu.tealhelix.catalogue.v1.types.HasFeedId;
import eu.tealhelix.catalogue.v1.types.HasIngestionRunId;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class IngestionRunDaoImpl implements IngestionRunDao {
	@Override
	public Uni<Void> create(ReactivePersistenceTxContext tx, List<IngestionRun> runs) {
		return tx.persistAll(runs.stream().map(run -> toEntity(tx, run)).toArray());
	}

	@Override
	public Uni<Void> update(ReactivePersistenceTxContext tx, IngestionRun run) {
		return tx.merge(toEntity(tx, run)).replaceWithVoid();
	}

	@Override
	public Uni<Boolean> updateHeld(ReactivePersistenceTxContext tx, IngestionRun run) {
		var entity = toEntity(tx, run);
		var cb = tx.getCriteriaBuilder();
		var u = cb.createCriteriaUpdate(IngestionRunEntity.class);
		var stored = u.from(IngestionRunEntity.class);
		u.set(stored.get(IngestionRunEntity_.status), entity.getStatus());
		u.set(stored.get(IngestionRunEntity_.finishedAt), entity.getFinishedAt());
		u.set(stored.get(IngestionRunEntity_.countTotal), entity.getCountTotal());
		u.set(stored.get(IngestionRunEntity_.countValid), entity.getCountValid());
		u.set(stored.get(IngestionRunEntity_.countRejected), entity.getCountRejected());
		u.set(stored.get(IngestionRunEntity_.countAdded), entity.getCountAdded());
		u.set(stored.get(IngestionRunEntity_.countChanged), entity.getCountChanged());
		u.set(stored.get(IngestionRunEntity_.countUnchanged), entity.getCountUnchanged());
		u.set(stored.get(IngestionRunEntity_.countMissing), entity.getCountMissing());
		u.set(stored.get(IngestionRunEntity_.countDeactivated), entity.getCountDeactivated());
		u.where(
				cb.equal(stored.get(IngestionRunEntity_.id), entity.getId()),
				cb.equal(stored.get(IngestionRunEntity_.status), IngestionRunStatus.HELD)
		);
		return tx.createUpdate(u).execute().map(updated -> updated == 1);
	}

	@Override
	public Uni<IngestionRun> findNextToProcess(ReactivePersistenceContext em, HasCatalogueId catalogueId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(IngestionRunEntity.class);
		var run = q.from(IngestionRunEntity.class);
		var feed = fetchFeed(run);
		var waitedFor = q.subquery(IngestionRunEntity.class);
		var busy = waitedFor.from(IngestionRunEntity.class);
		waitedFor.select(busy).where(
				cb.equal(busy.get(IngestionRunEntity_.catalogueId), catalogueId.getId().asString()),
				busy.get(IngestionRunEntity_.status).in(IngestionRunStatus.PROCESSING, IngestionRunStatus.HELD)
		);
		q.where(
				cb.equal(run.get(IngestionRunEntity_.catalogueId), catalogueId.getId().asString()),
				cb.equal(run.get(IngestionRunEntity_.status), IngestionRunStatus.RECEIVED),
				cb.not(cb.exists(waitedFor))
		);
		q.orderBy(cb.asc(feed.get(FeedEntity_.receivedAt)));
		return em.createQuery(q).setMaxResults(1).getResultList()
				.map(runs -> runs.isEmpty() ? null : toIngestionRun(runs.getFirst()));
	}

	@Override
	public Uni<List<CatalogueId>> findCataloguesWithReceivedRuns(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(String.class);
		var run = q.from(IngestionRunEntity.class);
		q.select(run.get(IngestionRunEntity_.catalogueId)).distinct(true);
		q.where(cb.equal(run.get(IngestionRunEntity_.status), IngestionRunStatus.RECEIVED));
		return em.createQuery(q).getResultList()
				.map(catalogueIds -> catalogueIds.stream().<CatalogueId>map(CatalogueIdImpl::new).toList());
	}

	@Override
	public Uni<Integer> resetProcessing(ReactivePersistenceTxContext tx) {
		var cb = tx.getCriteriaBuilder();
		var u = cb.createCriteriaUpdate(IngestionRunEntity.class);
		var run = u.from(IngestionRunEntity.class);
		u.set(run.get(IngestionRunEntity_.status), IngestionRunStatus.RECEIVED);
		u.where(cb.equal(run.get(IngestionRunEntity_.status), IngestionRunStatus.PROCESSING));
		return tx.createUpdate(u).execute();
	}

	@Override
	public Uni<Boolean> hasSnapshotReceivedAfter(ReactivePersistenceContext em, HasCatalogueId catalogueId, LocalDateTime receivedAt) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(UUID.class);
		var run = q.from(IngestionRunEntity.class);
		var feed = run.join(IngestionRunEntity_.feed);
		q.select(run.get(IngestionRunEntity_.id)).where(
				cb.equal(run.get(IngestionRunEntity_.catalogueId), catalogueId.getId().asString()),
				cb.equal(feed.get(FeedEntity_.feedType), FeedType.SNAPSHOT),
				cb.greaterThan(feed.get(FeedEntity_.receivedAt), receivedAt),
				cb.notEqual(run.get(IngestionRunEntity_.status), IngestionRunStatus.FAILED)
		);
		return em.createQuery(q).setMaxResults(1).getResultList().map(runs -> !runs.isEmpty());
	}

	@Override
	public Uni<Void> createRejects(ReactivePersistenceTxContext tx, HasIngestionRunId runId, List<IngestionReject> rejects) {
		if (rejects.isEmpty()) return Uni.createFrom().voidItem();
		return tx.persistAll(rejects.stream().map(reject -> toEntity(runId, reject)).toArray());
	}

	@Override
	public Uni<Void> deleteRejects(ReactivePersistenceTxContext tx, HasIngestionRunId runId) {
		var cb = tx.getCriteriaBuilder();
		var d = cb.createCriteriaDelete(IngestionRejectEntity.class);
		var reject = d.from(IngestionRejectEntity.class);
		d.where(cb.equal(reject.get(IngestionRejectEntity_.ingestionRunId), runId.getId().asUuid()));
		return tx.createDelete(d).execute().replaceWithVoid();
	}

	@Override
	public Uni<IngestionRun> find(ReactivePersistenceContext em, HasIngestionRunId runId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(IngestionRunEntity.class);
		var run = q.from(IngestionRunEntity.class);
		fetchFeed(run);
		q.where(cb.equal(run.get(IngestionRunEntity_.id), runId.getId().asUuid()));
		return em.createQuery(q).getResultList().map(runs -> runs.isEmpty() ? null : toIngestionRun(runs.getFirst()));
	}

	@Override
	public Uni<List<IngestionRun>> findOfFeed(ReactivePersistenceContext em, HasFeedId feedId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(IngestionRunEntity.class);
		var run = q.from(IngestionRunEntity.class);
		var feed = fetchFeed(run);
		q.where(cb.equal(feed.get(FeedEntity_.id), feedId.getId().asUuid()));
		q.orderBy(cb.asc(run.get(IngestionRunEntity_.catalogueId)), cb.asc(run.get(IngestionRunEntity_.startedAt), Nulls.LAST));
		return em.createQuery(q).getResultList().map(runs -> runs.stream().map(IngestionRunDaoImpl::toIngestionRun).toList());
	}

	@Override
	public Uni<List<IngestionReject>> findRejects(ReactivePersistenceContext em, HasIngestionRunId runId, int offset, int limit) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(IngestionRejectEntity.class);
		var reject = q.from(IngestionRejectEntity.class);
		q.where(cb.equal(reject.get(IngestionRejectEntity_.ingestionRunId), runId.getId().asUuid()));
		q.orderBy(cb.asc(reject.get(IngestionRejectEntity_.position)));
		return em.createQuery(q).setFirstResult(offset).setMaxResults(limit).getResultList()
				.map(rejects -> rejects.stream().map(IngestionRunDaoImpl::toIngestionReject).toList());
	}

	/**
	 * Every run read is read with its Feed. A fetch of Hibernate is also a join, which the query can filter and order by.
	 */
	@SuppressWarnings("unchecked")
	private static Join<IngestionRunEntity, FeedEntity> fetchFeed(Root<IngestionRunEntity> run) {
		return (Join<IngestionRunEntity, FeedEntity>) run.fetch(IngestionRunEntity_.feed);
	}

	/**
	 * The Feed is referred to, never written: it is recorded before its runs, and does not change.
	 */
	private static IngestionRunEntity toEntity(ReactivePersistenceContext em, IngestionRun run) {
		var entity = new IngestionRunEntity();
		entity.setId(run.getId().asUuid());
		entity.setFeed(em.getReference(FeedEntity.class, run.getFeed().getId().asUuid()));
		entity.setCatalogueId(run.getCatalogueId().asString());
		entity.setStatus(run.getStatus());
		entity.setAdapter(run.getAdapter());
		entity.setAdapterVersion(run.getAdapterVersion());
		entity.setStartedAt(run.getStartedAt());
		entity.setFinishedAt(run.getFinishedAt());
		var counts = run.getCounts();
		if (counts != null) {
			entity.setCountTotal(counts.getTotal());
			entity.setCountValid(counts.getValid());
			entity.setCountRejected(counts.getRejected());
			entity.setCountAdded(counts.getAdded());
			entity.setCountChanged(counts.getChanged());
			entity.setCountUnchanged(counts.getUnchanged());
			entity.setCountMissing(counts.getMissing());
			entity.setCountDeactivated(counts.getDeactivated());
		}
		entity.setError(run.getError());
		entity.setReplayOfRunId(run.getReplayOfRunId() != null ? run.getReplayOfRunId().asUuid() : null);
		return entity;
	}

	private static IngestionRejectEntity toEntity(HasIngestionRunId runId, IngestionReject reject) {
		var entity = new IngestionRejectEntity();
		entity.setIngestionRunId(runId.getId().asUuid());
		entity.setPosition(reject.getPosition());
		entity.setProductKey(reject.getProductKey() != null ? reject.getProductKey().asString() : null);
		entity.setReason(reject.getReason());
		return entity;
	}

	private static IngestionRun toIngestionRun(IngestionRunEntity entity) {
		return ImmutableIngestionRun.builder()
				.id(new IngestionRunIdImpl(entity.getId().toString()))
				.feed(FeedDaoImpl.toFeed(entity.getFeed()))
				.catalogueId(new CatalogueIdImpl(entity.getCatalogueId()))
				.status(entity.getStatus())
				.adapter(entity.getAdapter())
				.adapterVersion(entity.getAdapterVersion())
				.startedAt(entity.getStartedAt())
				.finishedAt(entity.getFinishedAt())
				.counts(toCounts(entity))
				.error(entity.getError())
				.replayOfRunId(entity.getReplayOfRunId() != null ? new IngestionRunIdImpl(entity.getReplayOfRunId().toString()) : null)
				.build();
	}

	/**
	 * The counts are written all at once when processing finishes, so the total stands for all of them.
	 */
	private static IngestionRunCounts toCounts(IngestionRunEntity entity) {
		if (entity.getCountTotal() == null) return null;
		return ImmutableIngestionRunCounts.builder()
				.total(entity.getCountTotal())
				.valid(entity.getCountValid())
				.rejected(entity.getCountRejected())
				.added(entity.getCountAdded())
				.changed(entity.getCountChanged())
				.unchanged(entity.getCountUnchanged())
				.missing(entity.getCountMissing())
				.deactivated(entity.getCountDeactivated())
				.build();
	}

	private static IngestionReject toIngestionReject(IngestionRejectEntity entity) {
		return ImmutableIngestionReject.builder()
				.position(entity.getPosition())
				.productKey(entity.getProductKey() != null ? new ProductKeyImpl(entity.getProductKey()) : null)
				.reason(entity.getReason())
				.build();
	}
}
