package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.tealhelix.howibuy.dao.impl.TestEntities.catalogue;
import static eu.tealhelix.howibuy.dao.impl.TestEntities.retailer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.BiFunction;

import eu.tealhelix.common.dao.EntityAlreadyExistsException;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.common.types.EmailAddress;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.jpa.PurchasedBasketEntity;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity;
import eu.tealhelix.howibuy.services.model.XpAndStreak;
import eu.tealhelix.howibuy.services.model.XpStanding;
import eu.tealhelix.howibuy.v1.types.BasketAssessmentOutcomeType;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class UserProfileDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;
	private static final UUID USER_ID = UUID.fromString("2e788895-0503-4777-a7bd-24e5d61db5b1");
	private static final UUID USER_WITH_EMAIL_ID = UUID.fromString("518cae6a-f2b2-4454-b74d-f2404feab2f5");
	private static final String IDM_ID = "IDM ID";
	private static final String USER_NAME = "User Name";
	private static final String EMAIL = "bob@krusty-krab.com";
	private static final String IDM_ID_OF_NEW_USER = "IDM ID OF NEW USER";
	private static final String EMAIL_OF_NEW_USER = "sandy@treedome.com";
	private static final UUID UNKNOWN_USER_ID = UUID.fromString("9d4b8f2e-6a13-4c57-b0e9-3f8a2d61c7a4");
	private static final UUID RETAILER_ID = UUID.fromString("6c1f8a42-9e37-4b05-a2d8-3f7e0b91c654");
	private static final String CATALOGUE_ID = "test-lt";

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "howibuy.db.changelog.xml", "test");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@Test
	@Order(1)
	void testCreation(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserProfileDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var returnedUser = factory.withTransaction(sut::createAutoUser).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertNotNull(returnedUser);
		assertNotNull(returnedUser.getId());
		assertNull(returnedUser.getEmail());
		var actualUser = factory.withTransaction(tx ->
				tx.find(UserProfileEntity.class, returnedUser.getId().asUuid())
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertNotNull(actualUser);
		assertNotNull(actualUser.getId());
		assertEquals(returnedUser.getId().asUuid(), actualUser.getId());
	}

	@Test
	@Order(2)
	void testFindByIdmId(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserProfileDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		factory.withTransaction(tx -> {
			var user = new UserProfileEntity();
			user.setId(USER_ID);
			return tx.persist(user);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var missing = factory.withoutTransaction(em ->
				sut.findByIdmId(em, IDM_ID, USER_NAME)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertTrue(missing.isEmpty());

		factory.withTransaction(tx ->
				tx.find(UserProfileEntity.class, USER_ID)
						.invoke(u -> u.setIdmId(IDM_ID))
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var user = factory.withoutTransaction(em ->
				sut.findByIdmId(em, IDM_ID, USER_NAME)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).orElseThrow();
		assertEquals(USER_ID, user.getId().asUuid());
		assertEquals(USER_NAME, user.getName());
	}

	@Test
	@Order(3)
	void testRequireById(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserProfileDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var userId = new UserIdImpl(USER_WITH_EMAIL_ID.toString());

		var subscriber = UniAssertSubscriber.create();
		factory.withoutTransaction(em ->
				sut.requireById(em, userId, USER_NAME)
		).subscribe().withSubscriber(subscriber);
		subscriber.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).assertFailedWith(NotFoundException.class);

		factory.withTransaction(tx -> {
			var user = new UserProfileEntity();
			user.setId(USER_WITH_EMAIL_ID);
			user.setEmail(EMAIL);
			return tx.persist(user);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var user = factory.withoutTransaction(em ->
				sut.requireById(em, userId, USER_NAME)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertEquals(EMAIL, user.getEmail().asString());
	}

	@Test
	@Order(4)
	void testCreateFromIdm(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserProfileDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var returnedUser = factory.withTransaction(tx ->
				sut.createFromIdm(tx, IDM_ID_OF_NEW_USER, USER_NAME, EmailAddress.of(EMAIL_OF_NEW_USER))
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertNotNull(returnedUser.getId());
		assertEquals(USER_NAME, returnedUser.getName());
		assertEquals(EMAIL_OF_NEW_USER, returnedUser.getEmail().asString());
		assertTrue(returnedUser.isPerson());

		var actualUser = factory.withoutTransaction(em ->
				em.find(UserProfileEntity.class, returnedUser.getId().asUuid())
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertEquals(IDM_ID_OF_NEW_USER, actualUser.getIdmId());
		assertEquals(EMAIL_OF_NEW_USER, actualUser.getEmail());
		assertNull(actualUser.getEmailConsent());
	}

	@Test
	@Order(5)
	void testCreateFromIdmRefusesAnIdmIdAlreadyTaken(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserProfileDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var subscriber = UniAssertSubscriber.create();
		factory.withTransaction(tx ->
				sut.createFromIdm(tx, IDM_ID_OF_NEW_USER, USER_NAME, null)
		).subscribe().withSubscriber(subscriber);
		subscriber.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).assertFailedWith(EntityAlreadyExistsException.class);
	}

	@Test
	@Order(6)
	void testXpTotalStartsAtZeroAndAddsUp(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserProfileDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var user = factory.withTransaction(sut::createAutoUser).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(0, xpAndStreakOf(factory, sut, user.getId()).getXpTotal());

		factory.withTransaction(tx -> sut.addXp(tx, user.getId(), 3)).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.addXp(tx, user.getId(), 4)).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var xpAndStreak = xpAndStreakOf(factory, sut, user.getId());
		assertEquals(7, xpAndStreak.getXpTotal());
		assertEquals(0, xpAndStreak.getStreak());
	}

	@Test
	@Order(7)
	void testStreakStartsAtZeroIncreasesByOneAndResets(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserProfileDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var user = factory.withTransaction(sut::createAutoUser).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(0, xpAndStreakOf(factory, sut, user.getId()).getStreak());
		assertEquals(1, streakAfter(factory, sut, user.getId(), sut::increaseStreak));
		assertEquals(2, streakAfter(factory, sut, user.getId(), sut::increaseStreak));
		assertEquals(0, streakAfter(factory, sut, user.getId(), sut::resetStreak));
		assertEquals(1, streakAfter(factory, sut, user.getId(), sut::increaseStreak));
		assertEquals(0, xpAndStreakOf(factory, sut, user.getId()).getXpTotal());
	}

	@Test
	@Order(8)
	void testXpAndStreakOfAnUnknownUserAreNotFound(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserProfileDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var userId = new UserIdImpl(UNKNOWN_USER_ID.toString());

		var requireSubscriber = UniAssertSubscriber.create();
		factory.withoutTransaction(em -> sut.requireXpAndStreak(em, userId)).subscribe().withSubscriber(requireSubscriber);
		requireSubscriber.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).assertFailedWith(NotFoundException.class);

		var addSubscriber = UniAssertSubscriber.create();
		factory.withTransaction(tx -> sut.addXp(tx, userId, 3)).subscribe().withSubscriber(addSubscriber);
		addSubscriber.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).assertFailedWith(NotFoundException.class);

		var increaseSubscriber = UniAssertSubscriber.create();
		factory.withTransaction(tx -> sut.increaseStreak(tx, userId)).subscribe().withSubscriber(increaseSubscriber);
		increaseSubscriber.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).assertFailedWith(NotFoundException.class);

		var resetSubscriber = UniAssertSubscriber.create();
		factory.withTransaction(tx -> sut.resetStreak(tx, userId)).subscribe().withSubscriber(resetSubscriber);
		resetSubscriber.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).assertFailedWith(NotFoundException.class);
	}

	@Test
	@Order(9)
	void testLocksTheProfileOfAUserAndFailsForAnUnknownOne(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserProfileDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var lockSubscriber = UniAssertSubscriber.<Void>create();
		factory.withTransaction(tx -> sut.lock(tx, new UserIdImpl(USER_ID.toString()))).subscribe().withSubscriber(lockSubscriber);
		lockSubscriber.awaitItem(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var unknownSubscriber = UniAssertSubscriber.<Void>create();
		factory.withTransaction(tx -> sut.lock(tx, new UserIdImpl(UNKNOWN_USER_ID.toString()))).subscribe().withSubscriber(unknownSubscriber);
		unknownSubscriber.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).assertFailedWith(NotFoundException.class);
	}

	/**
	 * The other users with at least one Purchased Basket count once each, however many baskets they have; of them, those
	 * with a lower XP total are behind the user, and one with the same XP total is not. A user without a basket counts
	 * in neither, however low their XP total.
	 */
	@Test
	@Order(10)
	void testXpStandingAmongTheOtherUsersWithABasket(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserProfileDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var user = profile();
		var lower = profile();
		var same = profile();
		var higher = profile();
		var withoutBasket = profile();
		sessionFactory.withTransaction(session -> session.persistAll(
						retailer(RETAILER_ID, "Test Retailer"), catalogue(CATALOGUE_ID, RETAILER_ID, "LT", "lt"),
						user, lower, same, higher, withoutBasket,
						basketOf(user), basketOf(lower), basketOf(lower), basketOf(same), basketOf(higher)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.addXp(tx, idOf(user), 50)
						.chain(() -> sut.addXp(tx, idOf(lower), 30))
						.chain(() -> sut.addXp(tx, idOf(same), 50))
						.chain(() -> sut.addXp(tx, idOf(higher), 70))
						.chain(() -> sut.addXp(tx, idOf(withoutBasket), 10)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		XpStanding standing = factory.withoutTransaction(em -> sut.retrieveXpStanding(em, idOf(user))).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(3, standing.getOtherShoppers(), "lower, same and higher, the user and the user without a basket left out");
		assertEquals(1, standing.getBehind(), "lower");
	}

	@Test
	@Order(11)
	void testXpStandingOfAnUnknownUserIsNoneOfEither(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserProfileDaoImpl();

		XpStanding standing = new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.retrieveXpStanding(em, new UserIdImpl(UNKNOWN_USER_ID.toString())))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(0, standing.getOtherShoppers());
		assertEquals(0, standing.getBehind());
	}

	private static UserProfileEntity profile() {
		var profile = new UserProfileEntity();
		profile.setId(UUID.randomUUID());
		return profile;
	}

	private static UserId idOf(UserProfileEntity profile) {
		return new UserIdImpl(profile.getId().toString());
	}

	private static PurchasedBasketEntity basketOf(UserProfileEntity profile) {
		var basket = new PurchasedBasketEntity();
		basket.setId(UUID.randomUUID());
		basket.setUserProfileId(profile.getId());
		basket.setRetailerId(RETAILER_ID);
		basket.setCatalogueId(CATALOGUE_ID);
		basket.setPurchasedAt(LocalDateTime.of(2026, 10, 31, 9, 15, 30));
		basket.setRecordedAt(LocalDateTime.of(2026, 10, 31, 9, 20, 45));
		basket.setOutcomeType(BasketAssessmentOutcomeType.NOTHING_TO_ASSESS);
		return basket;
	}

	private static XpAndStreak xpAndStreakOf(ReactivePersistenceContextFactory factory, UserProfileDaoImpl sut, UserId userId) {
		return factory.withoutTransaction(em -> sut.requireXpAndStreak(em, userId)).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	/**
	 * The user's Streak once changed in a transaction of its own.
	 */
	private static int streakAfter(
			ReactivePersistenceContextFactory factory, UserProfileDaoImpl sut, UserId userId, BiFunction<ReactivePersistenceTxContext, UserId, Uni<Void>> change) {
		factory.withTransaction(tx -> change.apply(tx, userId)).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		return xpAndStreakOf(factory, sut, userId).getStreak();
	}
}
