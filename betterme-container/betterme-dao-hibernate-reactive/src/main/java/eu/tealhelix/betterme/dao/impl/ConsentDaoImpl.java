package eu.tealhelix.betterme.dao.impl;

import static eu.tealhelix.betterme.dao.jpa.values.ConsentState.GRANTED;
import static eu.tealhelix.betterme.dao.jpa.values.ConsentState.REVOKED;

import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.betterme.dao.ConsentDao;
import eu.tealhelix.betterme.dao.jpa.ConsentEntity;
import eu.tealhelix.betterme.dao.jpa.RetailerEntity;
import eu.tealhelix.betterme.dao.jpa.UserProfileEntity;
import eu.tealhelix.betterme.dao.jpa.values.ConsentPK;
import eu.tealhelix.betterme.v1.types.HasRetailerId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
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
		var id = new ConsentPK(uuids.userId(), uuids.retailerId());
		return em.find(ConsentEntity.class, id).map(Optional::ofNullable);
	}

	@Override
	public Uni<Boolean> updateConsentToRetailer(ReactivePersistenceTxContext tx, HasUserId userId, HasRetailerId retailerId, boolean consent) {
		return makeUserAndRetailerUuids(userId, retailerId)
				.flatMap(userAndRetailerUuids ->
						findConsentEntity(tx, userAndRetailerUuids)
								.flatMap(optConsent ->
										optConsent
												.map(c -> {
													var oldConsentValue = c.getState() == null ? null : (c.getState() == GRANTED);
													c.setState(consent ? GRANTED : REVOKED);
													return Uni.createFrom().item(oldConsentValue);
												})
												.orElseGet(() -> createConsentEntity(tx, userAndRetailerUuids.userId(), userAndRetailerUuids.retailerId(), consent))
								)
				);
	}

	private Uni<Boolean> createConsentEntity(ReactivePersistenceTxContext tx, UUID userId, UUID retailerId, boolean consent) {
		var c = new ConsentEntity();
		c.setUser(tx.getReference(UserProfileEntity.class, userId));
		c.setRetailer(tx.getReference(RetailerEntity.class, retailerId));
		c.setState(consent ? GRANTED : REVOKED);
		return tx.persist(c).replaceWith((Boolean) null);
	}
}
