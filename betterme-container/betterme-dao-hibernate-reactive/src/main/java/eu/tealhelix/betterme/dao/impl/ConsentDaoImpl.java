package eu.tealhelix.betterme.dao.impl;

import static eu.tealhelix.betterme.dao.jpa.values.ConsentState.GRANTED;

import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;

import eu.tealhelix.betterme.dao.ConsentDao;
import eu.tealhelix.betterme.dao.jpa.ConsentEntity;
import eu.tealhelix.betterme.dao.jpa.ConsentEntity_;
import eu.tealhelix.betterme.v1.types.HasRetailerId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.v1.types.HasUserId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class ConsentDaoImpl implements ConsentDao {
	@Override
	public Uni<Boolean> hasConsentedToRetailer(ReactivePersistenceContext em, HasUserId userId, HasRetailerId retailerId) {
		return makeUserAndRetailerUuids(userId, retailerId)
				.flatMap(userAndRetailerUuids -> findConsentEntity(em, userAndRetailerUuids))
				.map(optConsent -> optConsent.map(c -> c.getState() == GRANTED).orElse(false));
	}

	private Uni<UserAndRetailerUuids> makeUserAndRetailerUuids(HasUserId userId, HasRetailerId retailerId) {
		String argName = "userId";
		String idAsString = userId.getId().asString();
		try {
			var userUuid = UUID.fromString(idAsString);
			argName = "retailerId";
			idAsString = retailerId.getId().asString();
			var retailerUuid = UUID.fromString(idAsString);
			return Uni.createFrom().item(new UserAndRetailerUuids(userUuid, retailerUuid));
		} catch (IllegalArgumentException e) {
			return Uni.createFrom().failure(new IllegalArgumentException(String.format("Cannot convert %s to UUID: %s", argName, idAsString), e));
		}
	}

	private Uni<Optional<ConsentEntity>> findConsentEntity(ReactivePersistenceContext em, UserAndRetailerUuids uuids) {
		var cb = em.getCriteriaBuilder();
		var query = cb.createQuery(ConsentEntity.class);
		Root<ConsentEntity> root = query.from(ConsentEntity.class);
		query.where(
				cb.equal(root.get(ConsentEntity_.userId), uuids.userId()),
				cb.equal(root.get(ConsentEntity_.retailerId), uuids.retailerId())
		);
		return em.createQuery(query).getSingleOptionalResult();
	}
}
