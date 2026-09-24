package eu.tealhelix.howibuy.services.generic.impl;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import eu.tealhelix.common.dao.EntityAlreadyExistsException;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.EmailAddress;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * How a user the IDM vouches for becomes a user of this application: the profile of one seen before is found, one never
 * seen before gets a profile on the spot, and of two requests racing to create the same profile the loser takes the
 * winner's. A user already known by the id this application gave them is looked up by that id. That the database
 * refuses the second profile is covered by {@code UserProfileDaoImplTest}.
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final String IDM_ID = "a7c1a3d4-6f55-4a8e-9d0e-1f2b3c4d5e6f";
	private static final String USER_NAME = "sandy@treedome.com";
	private static final EmailAddress EMAIL = EmailAddress.of("sandy@treedome.com");
	private static final User USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), USER_NAME, EMAIL, false, false);

	@Mock
	UserProfileDao userProfileDao;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	private UserServiceImpl sut;

	@BeforeEach
	void setUp() {
		sut = new UserServiceImpl(userProfileDao, persistenceContextFactory);
	}

	@Test
	void aUserSeenBeforeKeepsTheirProfile() {
		when(userProfileDao.findByIdmId(any(ReactivePersistenceContext.class), eq(IDM_ID), eq(USER_NAME)))
				.thenReturn(Uni.createFrom().item(Optional.of(USER)));

		var user = sut.findOrCreateUserFromValidIdmId(IDM_ID, USER_NAME, EMAIL).await().atMost(WAIT);

		assertSame(USER, user);
		verify(userProfileDao, never()).createFromIdm(any(), any(), any(), any());
	}

	@Test
	void aUserNeverSeenBeforeGetsAProfile() {
		when(userProfileDao.findByIdmId(any(ReactivePersistenceContext.class), eq(IDM_ID), eq(USER_NAME)))
				.thenReturn(Uni.createFrom().item(Optional.empty()));
		when(userProfileDao.createFromIdm(any(ReactivePersistenceTxContext.class), eq(IDM_ID), eq(USER_NAME), eq(EMAIL)))
				.thenReturn(Uni.createFrom().item(USER));

		var user = sut.findOrCreateUserFromValidIdmId(IDM_ID, USER_NAME, EMAIL).await().atMost(WAIT);

		assertSame(USER, user);
	}

	/**
	 * A single-page application fires several requests as soon as the user logs in, so the first requests of a new user
	 * may all find no profile and all try to create one; only one of them can.
	 */
	@Test
	void aRequestThatLosesTheRaceToCreateTheProfileTakesTheWinnersProfile() {
		when(userProfileDao.findByIdmId(any(ReactivePersistenceContext.class), eq(IDM_ID), eq(USER_NAME)))
				.thenReturn(Uni.createFrom().item(Optional.empty()))
				.thenReturn(Uni.createFrom().item(Optional.of(USER)));
		when(userProfileDao.createFromIdm(any(ReactivePersistenceTxContext.class), eq(IDM_ID), eq(USER_NAME), eq(EMAIL)))
				.thenReturn(Uni.createFrom().failure(new EntityAlreadyExistsException("taken", null)));

		var user = sut.findOrCreateUserFromValidIdmId(IDM_ID, USER_NAME, EMAIL).await().atMost(WAIT);

		assertSame(USER, user);
	}

	/**
	 * The database refuses a second profile only once the first is committed, so a refused profile can always be read
	 * back; one that cannot means something deleted it in between, which nothing in the application does.
	 */
	@Test
	void aProfileThatVanishesAfterItsCreationWasRefusedIsAnIllegalState() {
		when(userProfileDao.findByIdmId(any(ReactivePersistenceContext.class), eq(IDM_ID), eq(USER_NAME)))
				.thenReturn(Uni.createFrom().item(Optional.empty()));
		when(userProfileDao.createFromIdm(any(ReactivePersistenceTxContext.class), eq(IDM_ID), eq(USER_NAME), eq(EMAIL)))
				.thenReturn(Uni.createFrom().failure(new EntityAlreadyExistsException("taken", null)));

		var failure = UniAssertSubscriber.create();
		sut.findOrCreateUserFromValidIdmId(IDM_ID, USER_NAME, EMAIL).subscribe().withSubscriber(failure);

		failure.awaitFailure(WAIT).assertFailedWith(IllegalStateException.class, IDM_ID);
	}

	@Test
	void aUserIsRequiredByTheIdThisApplicationKnowsThemBy() {
		when(userProfileDao.requireById(any(ReactivePersistenceContext.class), eq(USER.getId()), eq(USER_NAME), eq(false)))
				.thenReturn(Uni.createFrom().item(USER));

		var user = sut.requireUserWithId(USER.getId(), USER_NAME, false).await().atMost(WAIT);

		assertSame(USER, user);
	}

	/**
	 * {@code TokenHelperImpl} turns exactly this failure into a refusal of the token, so it has to reach the caller as it
	 * is.
	 */
	@Test
	void aUserWithoutAProfileIsNotFound() {
		when(userProfileDao.requireById(any(ReactivePersistenceContext.class), eq(USER.getId()), eq(USER_NAME), eq(false)))
				.thenReturn(Uni.createFrom().failure(new NotFoundException(USER.getId())));

		var failure = UniAssertSubscriber.create();
		sut.requireUserWithId(USER.getId(), USER_NAME, false).subscribe().withSubscriber(failure);

		failure.awaitFailure(WAIT).assertFailedWith(NotFoundException.class);
	}
}
