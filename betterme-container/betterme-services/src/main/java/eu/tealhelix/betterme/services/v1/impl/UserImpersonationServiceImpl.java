package eu.tealhelix.betterme.services.v1.impl;

import static java.lang.Boolean.TRUE;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.betterme.dao.ConsentDao;
import eu.tealhelix.betterme.dao.CorrelationIdDao;
import eu.tealhelix.betterme.dao.UserProfileDao;
import eu.tealhelix.betterme.services.v1.UserImpersonationService;
import eu.tealhelix.betterme.services.v1.authz.BetterMeAuthorization;
import eu.tealhelix.betterme.v1.types.RetailerId;
import eu.tealhelix.betterme.v1.types.impl.GenericRetailerId;
import eu.tealhelix.common.dao.EntityNotFoundException;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.UserId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class UserImpersonationServiceImpl implements UserImpersonationService {
	private final BetterMeAuthorization authorization;
	private final CorrelationIdDao correlationIdDao;
	private final ConsentDao consentDao;
	private final UserProfileDao userProfileDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	@Inject
	public UserImpersonationServiceImpl(
			BetterMeAuthorization authorization,
			CorrelationIdDao correlationIdDao,
			ConsentDao consentDao,
			UserProfileDao userProfileDao,
			ReactivePersistenceContextFactory persistenceContextFactory
	) {
		this.authorization = authorization;
		this.correlationIdDao = correlationIdDao;
		this.consentDao = consentDao;
		this.userProfileDao = userProfileDao;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<User> impersonateUserAsRetailer(User currentUser, String correlationId) {
		return authorization.authorizeImpersonation(currentUser)
				.chain(() ->
						persistenceContextFactory.withTransaction(tx -> {
							var retailerId = new GenericRetailerId(currentUser.getId().asString());
							return correlationIdDao.requireByRetailerAndCorrelationId(tx, retailerId, correlationId)
									.flatMap(user -> checkConsent(tx, user.getId(), retailerId))
									.map(userProfileDao::toUser)
									.onFailure(EntityNotFoundException.class)
									.recoverWithUni(enfe -> createAndConfigureAutoUser(tx, retailerId, correlationId));
						})
				);
	}

	private Uni<UserId> checkConsent(ReactivePersistenceContext em, UserId userId, RetailerId retailerId) {
		return consentDao.hasConsentedToRetailer(em, userId, retailerId)
				.flatMap(hasConsented -> TRUE.equals(hasConsented)
						? Uni.createFrom().item(userId)
						: Uni.createFrom().failure(new NotAuthorizedException("User has not consented to retailer"))
				);
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
		return correlationIdDao.createCorrelation(tx, retailerId, correlationId, user)
				.replaceWith(user);
	}
}
