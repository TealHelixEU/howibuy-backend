package eu.tealhelix.betterme.services.v1.impl;

import static java.lang.Boolean.TRUE;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.betterme.dao.ConsentDao;
import eu.tealhelix.betterme.dao.CorrelationIdDao;
import eu.tealhelix.betterme.services.v1.UserImpersonationService;
import eu.tealhelix.betterme.services.v1.authz.BetterMeAuthorization;
import eu.tealhelix.betterme.v1.types.RetailerId;
import eu.tealhelix.betterme.v1.types.impl.GenericRetailerId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.UserId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class UserImpersonationServiceImpl implements UserImpersonationService {
	private final BetterMeAuthorization authorization;
	private final CorrelationIdDao correlationIdDao;
	private final ConsentDao consentDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	@Inject
	public UserImpersonationServiceImpl(
			BetterMeAuthorization authorization,
			CorrelationIdDao correlationIdDao,
			ConsentDao consentDao,
			ReactivePersistenceContextFactory persistenceContextFactory
	) {
		this.authorization = authorization;
		this.correlationIdDao = correlationIdDao;
		this.consentDao = consentDao;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<User> impersonateUser(User currentUser, String correlationId) {
		return authorization.authorizeImpersonation(currentUser)
				.chain(() ->
						persistenceContextFactory.withTransaction(em -> {
							var retailerId = new GenericRetailerId(currentUser.getId().asString());
							return correlationIdDao.requireByRetailerAndCorrelationId(em, retailerId, correlationId)
									.flatMap(user -> {
										return checkConsent(em, user.getId(), retailerId)
												.replaceWith(() -> new UserImpl(user.getId(), null, null, false, false));
									});
						})
				);
	}

	private Uni<Void> checkConsent(ReactivePersistenceContext em, UserId userId, RetailerId retailerId) {
		return consentDao.hasConsentedToRetailer(em, userId, retailerId)
				.flatMap(hasConsented -> TRUE.equals(hasConsented)
						? Uni.createFrom().voidItem()
						: Uni.createFrom().failure(new NotAuthorizedException("User has not consented to retailer"))
				);
	}
}
