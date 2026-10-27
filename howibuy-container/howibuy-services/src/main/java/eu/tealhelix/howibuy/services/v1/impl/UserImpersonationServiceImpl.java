package eu.tealhelix.howibuy.services.v1.impl;

import static java.lang.Boolean.TRUE;

import java.util.function.BiFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.common.dao.EntityNotFoundException;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import eu.tealhelix.howibuy.dao.CatalogueDao;
import eu.tealhelix.howibuy.dao.ConsentDao;
import eu.tealhelix.howibuy.dao.CorrelationIdDao;
import eu.tealhelix.howibuy.dao.RetailerDao;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import eu.tealhelix.howibuy.services.v1.UserImpersonationService;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UserImpersonationServiceImpl implements UserImpersonationService {
	private static final Logger LOG = LoggerFactory.getLogger(UserImpersonationServiceImpl.class);

	private final TealHelixAuthorization authorization;
	private final CorrelationIdDao correlationIdDao;
	private final ConsentDao consentDao;
	private final UserProfileDao userProfileDao;
	private final RetailerDao retailerDao;
	private final CatalogueDao catalogueDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	@Inject
	public UserImpersonationServiceImpl(
			TealHelixAuthorization authorization,
			CorrelationIdDao correlationIdDao,
			ConsentDao consentDao,
			UserProfileDao userProfileDao,
			RetailerDao retailerDao,
			CatalogueDao catalogueDao,
			ReactivePersistenceContextFactory persistenceContextFactory
	) {
		this.authorization = authorization;
		this.correlationIdDao = correlationIdDao;
		this.consentDao = consentDao;
		this.userProfileDao = userProfileDao;
		this.retailerDao = retailerDao;
		this.catalogueDao = catalogueDao;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<User> impersonateUserAsRetailer(User currentUser, String correlationId) {
		return impersonateUserAsRetailer(currentUser, correlationId, this::requireActiveRetailer);
	}

	@Override
	public Uni<User> impersonateUserAsRetailer(User currentUser, String correlationId, CatalogueId catalogueId) {
		return impersonateUserAsRetailer(currentUser, correlationId, (em, retailerId) ->
				requireActiveRetailer(em, retailerId).chain(() -> requireActiveCatalogue(em, retailerId, catalogueId)));
	}

	/**
	 * @param requirements What the retailer must satisfy to impersonate a user, checked in the transaction of the
	 *                     impersonation, before the user is looked up
	 */
	private Uni<User> impersonateUserAsRetailer(User currentUser, String correlationId, BiFunction<ReactivePersistenceContext, RetailerId, Uni<Void>> requirements) {
		return authorization.authorizeImpersonation(currentUser)
				.invoke(() -> RequiredInputMissingException.throwIfRequiredInputMissing("correlationId", correlationId))
				.invoke(() -> BadInputValueException.throwForStringMaxLength("correlationId", correlationId, 100))
				.chain(() ->
						persistenceContextFactory.withTransaction(tx -> {
							LOG.info("Impersonating user as retailer {} -> correlationId: {}", currentUser.getId().asString(), correlationId);
							var retailerId = new GenericRetailerId(currentUser.getId().asString());
							return requirements.apply(tx, retailerId)
									.chain(() -> correlationIdDao.requireByRetailerAndCorrelationId(tx, retailerId, correlationId)
											.flatMap(user -> checkConsent(tx, user.getId(), retailerId))
											.map(userId -> createUserObjectForExistingCorrelationId(userId, retailerId, correlationId))
											.onFailure(EntityNotFoundException.class)
											.recoverWithUni(_ -> createAndConfigureAutoUser(tx, retailerId, correlationId)));
						})
				);
	}

	/**
	 * The IDM authenticates a client of the realm; whether that client is a retailer of this application, and whether it
	 * is still allowed to act, is decided here.
	 */
	private Uni<Void> requireActiveRetailer(ReactivePersistenceContext em, RetailerId retailerId) {
		return retailerDao.findActiveFlag(em, retailerId)
				.invoke(active -> {
					if (active == null) {
						LOG.error("Retailer {} is authenticated but absent from the database; IDM replication is out of sync", retailerId.asString());
						throw new NotAuthorizedException("Not a retailer of this application");
					} else if (!active) {
						LOG.warn("Impersonation refused to a deactivated retailer: {}", retailerId.asString());
						throw new NotAuthorizedException("Retailer is not active");
					}
				})
				.replaceWithVoid();
	}

	/**
	 * A retailer may serve a user only from a Catalogue of its own that is active. An unknown Catalogue and another
	 * retailer's are refused alike, so the answer tells a retailer nothing about the Catalogues of others.
	 */
	private Uni<Void> requireActiveCatalogue(ReactivePersistenceContext em, RetailerId retailerId, CatalogueId catalogueId) {
		return catalogueDao.findActiveFlag(em, retailerId, catalogueId)
				.invoke(active -> {
					if (!TRUE.equals(active)) {
						LOG.warn("Impersonation refused in a Catalogue that is not an active Catalogue of the retailer {}, catalogueId: {}", retailerId.asString(), catalogueId.asString());
						throw new NotAuthorizedException("Not an active Catalogue of the retailer");
					}
				})
				.replaceWithVoid();
	}

	private Uni<UserId> checkConsent(ReactivePersistenceContext em, UserId userId, RetailerId retailerId) {
		return consentDao.hasConsentedToRetailer(em, userId, retailerId)
				.map(hasConsented -> {
					if (TRUE.equals(hasConsented)) {
						return userId;
					} else {
						LOG.warn("No consent for impersonation {} -> userId: {}", retailerId.asString(), userId.asString());
						throw new NotAuthorizedException("User has not consented to retailer");
					}
				});
	}

	private User createUserObjectForExistingCorrelationId(UserId userId, RetailerId retailerId, String correlationId) {
		LOG.info("Found existing user for impersonation {} -> correlationId: {} userId: {}", retailerId.asString(), correlationId, userId.asString());
		return userProfileDao.toUser(userId);
	}

	private Uni<User> createAndConfigureAutoUser(ReactivePersistenceTxContext tx, RetailerId retailerId, String correlationId) {
		return userProfileDao.createAutoUser(tx)
				.flatMap(user -> consentToRetailerAndReturnTheUser(tx, user, retailerId))
				.flatMap(user -> createCorrelationAndReturnTheUser(tx, retailerId, correlationId, user));
	}

	private Uni<User> consentToRetailerAndReturnTheUser(ReactivePersistenceTxContext tx, User user, RetailerId retailerId) {
		return consentDao.updateConsentToRetailer(tx, user, retailerId, true)
				.replaceWith(user);
	}

	private Uni<User> createCorrelationAndReturnTheUser(ReactivePersistenceTxContext tx, RetailerId retailerId, String correlationId, User user) {
		LOG.info("Created new user impersonation entry {} -> correlationId: {} userId: {}", retailerId.asString(), correlationId, user.getId().asString());
		return correlationIdDao.createCorrelation(tx, retailerId, correlationId, user)
				.replaceWith(user);
	}
}
