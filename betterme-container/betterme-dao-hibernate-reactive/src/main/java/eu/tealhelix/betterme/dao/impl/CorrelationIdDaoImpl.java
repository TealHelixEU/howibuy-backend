package eu.tealhelix.betterme.dao.impl;

import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;

import eu.tealhelix.betterme.dao.CorrelationIdDao;
import eu.tealhelix.betterme.dao.jpa.CorrelationIdEntity;
import eu.tealhelix.betterme.dao.jpa.CorrelationIdEntity_;
import eu.tealhelix.betterme.dao.jpa.RetailerEntity_;
import eu.tealhelix.betterme.dao.jpa.UserProfileEntity;
import eu.tealhelix.betterme.dao.jpa.values.CorrelationIdPK;
import eu.tealhelix.betterme.v1.types.RetailerId;
import eu.tealhelix.common.dao.EntityNotFoundException;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.types.impl.EmailImpl;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class CorrelationIdDaoImpl implements CorrelationIdDao {
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

	private User toUser(UserProfileEntity u) {
		return new UserImpl(new UserIdImpl(u.getId().toString()), u.getEmail(), new EmailImpl(u.getEmail()), false, false);
	}

	private EntityNotFoundException notFound(RetailerId retailerId, String correlationId) {
		var id = new CorrelationIdPK(retailerId.asUuid(), correlationId);
		return new EntityNotFoundException(CorrelationIdEntity.class, id);
	}
}
