package eu.tealhelix.catalogue.dao.impl;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.catalogue.dao.IngestionRunDao;
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
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
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
	public Uni<Void> create(ReactivePersistenceTxContext tx, IngestionRun run) {
		return tx.persist(toEntity(run)).replaceWithVoid();
	}

	@Override
	public Uni<Void> update(ReactivePersistenceTxContext tx, IngestionRun run) {
		return tx.merge(toEntity(run)).replaceWithVoid();
	}

	@Override
	public Uni<Boolean> updateHeld(ReactivePersistenceTxContext tx, IngestionRun run) {
		var entity = toEntity(run);
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
	public Uni<IngestionRun> findNextToProcess(ReactivePersistenceContext em, CatalogueId catalogueId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(IngestionRunEntity.class);
		var run = q.from(IngestionRunEntity.class);
		var waitedFor = q.subquery(IngestionRunEntity.class);
		var busy = waitedFor.from(IngestionRunEntity.class);
		waitedFor.select(busy).where(
				cb.equal(busy.get(IngestionRunEntity_.catalogueId), catalogueId.asString()),
				busy.get(IngestionRunEntity_.status).in(IngestionRunStatus.PROCESSING, IngestionRunStatus.HELD)
		);
		q.where(
				cb.equal(run.get(IngestionRunEntity_.catalogueId), catalogueId.asString()),
				cb.equal(run.get(IngestionRunEntity_.status), IngestionRunStatus.RECEIVED),
				cb.not(cb.exists(waitedFor))
		);
		q.orderBy(cb.asc(run.get(IngestionRunEntity_.receivedAt)));
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
	public Uni<Void> createRejects(ReactivePersistenceTxContext tx, IngestionRunId runId, List<IngestionReject> rejects) {
		if (rejects.isEmpty()) return Uni.createFrom().voidItem();
		return tx.persistAll(rejects.stream().map(reject -> toEntity(runId, reject)).toArray());
	}

	@Override
	public Uni<Void> deleteRejects(ReactivePersistenceTxContext tx, IngestionRunId runId) {
		var cb = tx.getCriteriaBuilder();
		var d = cb.createCriteriaDelete(IngestionRejectEntity.class);
		var reject = d.from(IngestionRejectEntity.class);
		d.where(cb.equal(reject.get(IngestionRejectEntity_.ingestionRunId), runId.asUuid()));
		return tx.createDelete(d).execute().replaceWithVoid();
	}

	@Override
	public Uni<IngestionRun> find(ReactivePersistenceContext em, IngestionRunId runId) {
		return em.find(IngestionRunEntity.class, runId.asUuid()).map(entity -> entity != null ? toIngestionRun(entity) : null);
	}

	@Override
	public Uni<List<IngestionReject>> findRejects(ReactivePersistenceContext em, IngestionRunId runId, int offset, int limit) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(IngestionRejectEntity.class);
		var reject = q.from(IngestionRejectEntity.class);
		q.where(cb.equal(reject.get(IngestionRejectEntity_.ingestionRunId), runId.asUuid()));
		q.orderBy(cb.asc(reject.get(IngestionRejectEntity_.position)));
		return em.createQuery(q).setFirstResult(offset).setMaxResults(limit).getResultList()
				.map(rejects -> rejects.stream().map(IngestionRunDaoImpl::toIngestionReject).toList());
	}

	private static IngestionRunEntity toEntity(IngestionRun run) {
		var entity = new IngestionRunEntity();
		entity.setId(run.getId().asUuid());
		entity.setCatalogueId(run.getCatalogueId().asString());
		entity.setFeedType(run.getFeedType());
		entity.setStatus(run.getStatus());
		entity.setAdapter(run.getAdapter());
		entity.setAdapterVersion(run.getAdapterVersion());
		entity.setReceivedAt(run.getReceivedAt());
		entity.setStartedAt(run.getStartedAt());
		entity.setFinishedAt(run.getFinishedAt());
		entity.setFilePath(run.getFilePath());
		entity.setFileSize(run.getFileSize());
		entity.setFileSha256(run.getFileSha256());
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
		return entity;
	}

	private static IngestionRejectEntity toEntity(IngestionRunId runId, IngestionReject reject) {
		var entity = new IngestionRejectEntity();
		entity.setIngestionRunId(runId.asUuid());
		entity.setPosition(reject.getPosition());
		entity.setProductKey(reject.getProductKey() != null ? reject.getProductKey().asString() : null);
		entity.setReason(reject.getReason());
		return entity;
	}

	private static IngestionRun toIngestionRun(IngestionRunEntity entity) {
		return ImmutableIngestionRun.builder()
				.id(new IngestionRunIdImpl(entity.getId().toString()))
				.catalogueId(new CatalogueIdImpl(entity.getCatalogueId()))
				.feedType(entity.getFeedType())
				.status(entity.getStatus())
				.adapter(entity.getAdapter())
				.adapterVersion(entity.getAdapterVersion())
				.receivedAt(entity.getReceivedAt())
				.startedAt(entity.getStartedAt())
				.finishedAt(entity.getFinishedAt())
				.filePath(entity.getFilePath())
				.fileSize(entity.getFileSize())
				.fileSha256(entity.getFileSha256())
				.counts(toCounts(entity))
				.error(entity.getError())
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
