package eu.tealhelix.sfc.dao.impl;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.sfc.dao.AttemptDao;
import eu.tealhelix.sfc.dao.jpa.AttemptEntity;
import eu.tealhelix.sfc.dao.jpa.AttemptEntity_;
import eu.tealhelix.sfc.v1.types.AttemptStatus;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class AttemptDaoImpl implements AttemptDao {
	@Override
	public Uni<Optional<UUID>> findInProgressId(ReactivePersistenceContext em, UUID userId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(UUID.class);
		var root = q.from(AttemptEntity.class);
		q.select(root.get(AttemptEntity_.id))
				.where(cb.and(
						cb.equal(root.get(AttemptEntity_.userId), userId),
						cb.equal(root.get(AttemptEntity_.status), AttemptStatus.IN_PROGRESS)));
		return em.createQuery(q).getSingleOptionalResult();
	}

	@Override
	public Uni<Optional<LocalDateTime>> findLatestCompletedAt(ReactivePersistenceContext em, UUID userId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(LocalDateTime.class);
		var root = q.from(AttemptEntity.class);
		q.select(root.get(AttemptEntity_.completedAt))
				.where(cb.and(
						cb.equal(root.get(AttemptEntity_.userId), userId),
						cb.equal(root.get(AttemptEntity_.status), AttemptStatus.COMPLETED)))
				.orderBy(cb.desc(root.get(AttemptEntity_.completedAt)));
		return em.createQuery(q).setMaxResults(1).getSingleOptionalResult();
	}

	@Override
	public Uni<UUID> startInProgress(ReactivePersistenceTxContext tx, UUID userId) {
		var attempt = new AttemptEntity();
		var id = UUID.randomUUID();
		attempt.setId(id);
		attempt.setUserId(userId);
		attempt.setStatus(AttemptStatus.IN_PROGRESS);
		return tx.persist(attempt).replaceWith(id);
	}

	@Override
	public Uni<Void> complete(ReactivePersistenceTxContext tx, UUID attemptId, LocalDateTime completedAt) {
		return tx.find(AttemptEntity.class, attemptId).flatMap(attempt -> {
			attempt.setStatus(AttemptStatus.COMPLETED);
			attempt.setCompletedAt(completedAt);
			return Uni.createFrom().voidItem();
		});
	}
}
