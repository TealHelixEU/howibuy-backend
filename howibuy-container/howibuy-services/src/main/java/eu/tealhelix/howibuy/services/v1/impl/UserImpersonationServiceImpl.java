package eu.tealhelix.howibuy.services.v1.impl;

import static java.lang.Boolean.TRUE;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.EntityNotFoundException;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.howibuy.dao.ConsentDao;
import eu.tealhelix.howibuy.dao.CorrelationIdDao;
import eu.tealhelix.howibuy.dao.RetailerDao;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import eu.tealhelix.howibuy.services.v1.UserImpersonationService;
import eu.tealhelix.howibuy.services.v1.authz.HowiBuyAuthorization;
import eu.tealhelix.howibuy.v1.types.RetailerId;
import eu.tealhelix.howibuy.v1.types.impl.GenericRetailerId;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UserImpersonationServiceImpl implements UserImpersonationService {
	private static final Logger LOG = LoggerFactory.getLogger(UserImpersonationServiceImpl.class);

	private final HowiBuyAuthorization authorization;
	private final CorrelationIdDao correlationIdDao;
	private final ConsentDao consentDao;
	private final UserProfileDao userProfileDao;
	private final RetailerDao retailerDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	@Inject
	public UserImpersonationServiceImpl(
			HowiBuyAuthorization authorization,
			CorrelationIdDao correlationIdDao,
			ConsentDao consentDao,
			UserProfileDao userProfileDao,
			RetailerDao retailerDao,
			ReactivePersistenceContextFactory persistenceContextFactory
	) {
		this.authorization = authorization;
		this.correlationIdDao = correlationIdDao;
		this.consentDao = consentDao;
		this.userProfileDao = userProfileDao;
		this.retailerDao = retailerDao;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<User> impersonateUserAsRetailer(User currentUser, String correlationId) {
		return authorization.authorizeImpersonation(currentUser)
				.invoke(() -> RequiredInputMissingException.throwIfRequiredInputMissing("correlationId", correlationId))
				.invoke(() -> BadInputValueException.throwForStringMaxLength("correlationId", correlationId, 100))
				.chain(() ->
						persistenceContextFactory.withTransaction(tx -> {
							LOG.info("Impersonating user as retailer {} -> correlationId: {}", currentUser.getId().asString(), correlationId);
							var retailerId = new GenericRetailerId(currentUser.getId().asString());
							return requireRetailerExists(tx, retailerId)
									.chain(() -> correlationIdDao.requireByRetailerAndCorrelationId(tx, retailerId, correlationId)
											.flatMap(user -> checkConsent(tx, user.getId(), retailerId))
											.map(userId -> createUserObjectForExistingCorrelationId(userId, retailerId, correlationId))
											.onFailure(EntityNotFoundException.class)
											.recoverWithUni(_ -> createAndConfigureAutoUser(tx, retailerId, correlationId)));
						})
				);
	}

	private Uni<Void> requireRetailerExists(ReactivePersistenceContext em, RetailerId retailerId) {
		return retailerDao.exists(em, retailerId)
				.invoke(exists -> {
					if (!TRUE.equals(exists)) {
						LOG.error("Retailer {} is authenticated but absent from the database; IDM replication is out of sync", retailerId.asString());
						throw new IllegalStateException("Retailer not found in the database: " + retailerId.asString());
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
