package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.howibuy.dao.impl.UserProfileUtils.toUser;

import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;

import eu.tealhelix.howibuy.dao.CorrelationIdDao;
import eu.tealhelix.howibuy.dao.jpa.CorrelationIdEntity;
import eu.tealhelix.howibuy.dao.jpa.CorrelationIdEntity_;
import eu.tealhelix.howibuy.dao.jpa.RetailerEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerEntity_;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity;
import eu.tealhelix.howibuy.dao.jpa.values.CorrelationIdPK;
import eu.tealhelix.howibuy.v1.types.RetailerId;
import eu.tealhelix.common.dao.EntityNotFoundException;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.HasUserId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class CorrelationIdDaoImpl implements CorrelationIdDao {
	@Override
	public Uni<Void> createCorrelation(ReactivePersistenceTxContext tx, RetailerId retailerId, String correlationId, HasUserId userId) {
		var correlationIdEntity = new CorrelationIdEntity();
		correlationIdEntity.setRetailer(tx.getReference(RetailerEntity.class, retailerId.asUuid()));
		correlationIdEntity.setCorrelationId(correlationId);
		correlationIdEntity.setUser(tx.getReference(UserProfileEntity.class, userId.getId().asUuid()));
		return tx.persist(correlationIdEntity).replaceWithVoid();
	}

	@Override
	public Uni<User> requireByRetailerAndCorrelationId(ReactivePersistenceContext em, RetailerId retailerId, String correlationId) {
		return findCorrelationEntityAndUser(em, retailerId.asUuid(), correlationId)
				.map(opt -> opt.map(c -> toUser(c.getUser())).orElseThrow(() -> notFound(retailerId, correlationId)));
	}

	private Uni<Optional<CorrelationIdEntity>> findCorrelationEntityAndUser(ReactivePersistenceContext em, UUID retailerId, String correlationId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(CorrelationIdEntity.class);
		Root<CorrelationIdEntity> correlationIdEntity = q.from(CorrelationIdEntity.class);
		correlationIdEntity.fetch(CorrelationIdEntity_.user);
		q.where(
				cb.equal(correlationIdEntity.get(CorrelationIdEntity_.retailer).get(RetailerEntity_.id), retailerId),
				cb.equal(correlationIdEntity.get(CorrelationIdEntity_.correlationId), correlationId)
		);
		return em.createQuery(q).getSingleOptionalResult();
	}

	private EntityNotFoundException notFound(RetailerId retailerId, String correlationId) {
		var id = new CorrelationIdPK(retailerId.asUuid(), correlationId);
		return new EntityNotFoundException(CorrelationIdEntity.class, id);
	}
}
