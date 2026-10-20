package eu.tealhelix.catalogue.dao.impl;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.dao.jpa.IngestionRejectEntity;
import eu.tealhelix.catalogue.dao.jpa.IngestionRejectEntity_;
import eu.tealhelix.catalogue.dao.jpa.IngestionRunEntity;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionReject;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRun;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRunCounts;
import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.model.IngestionRunCounts;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
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
			entity.setCountDeactivated(counts.getDeactivated());
		}
		entity.setError(run.getError());
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
