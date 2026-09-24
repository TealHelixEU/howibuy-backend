package eu.tealhelix.howibuy.services.generic.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;

import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.dao.EntityAlreadyExistsException;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.generic.UserService;
import eu.tealhelix.common.types.EmailAddress;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UserServiceImpl implements UserService {
	private static final Logger LOG = LoggerFactory.getLogger(UserServiceImpl.class);

	private final UserProfileDao userProfileDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	public UserServiceImpl(UserProfileDao userProfileDao, ReactivePersistenceContextFactory persistenceContextFactory) {
		this.userProfileDao = userProfileDao;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<User> findOrCreateUserFromValidIdmId(String userIdFromIdm, String name, EmailAddress email) {
		return forc(
				persistenceContextFactory.withoutTransaction(em -> userProfileDao.findByIdmId(em, userIdFromIdm, name)),
				optionalUser -> optionalUser
						.map(existingUser -> Uni.createFrom().item(existingUser))
						.orElseGet(() -> createFromIdm(userIdFromIdm, name, email))
		);
	}

	/**
	 * Creates the profile of a user new to this application. Another request of the same user may have created it in the
	 * meantime, in which case the database refuses a second one, and the profile that request created is the user's.
	 * <p>
	 * The creation and the reading back each get a session of their own, and neither may run within the session of the
	 * lookup that came first: the session in which the database refused the profile still holds it, and would try to
	 * store it again.
	 */
	private Uni<User> createFromIdm(String userIdFromIdm, String name, EmailAddress email) {
		return persistenceContextFactory.withTransaction(tx -> userProfileDao.createFromIdm(tx, userIdFromIdm, name, email))
				.invoke(user -> LOG.info("Created the profile of a user new to the application, IDM id: {}", userIdFromIdm))
				.onFailure(EntityAlreadyExistsException.class)
				.recoverWithUni(() -> persistenceContextFactory.withoutTransaction(em -> userProfileDao.findByIdmId(em, userIdFromIdm, name))
						.map(profile -> profile.orElseThrow(() -> new IllegalStateException(
								"User profile vanished after its creation was refused as a duplicate, IDM id: " + userIdFromIdm))));
	}

	@Override
	public Uni<User> requireUserWithId(UserId userId, String name, boolean serviceFlag) {
		return persistenceContextFactory.withoutTransaction(em ->
				userProfileDao.requireById(em, userId, name, serviceFlag)
		);
	}
}
