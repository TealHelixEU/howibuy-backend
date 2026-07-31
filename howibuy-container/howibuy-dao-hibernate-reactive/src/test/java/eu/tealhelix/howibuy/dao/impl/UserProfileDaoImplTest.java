package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.UUID;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity;
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
	void testRequireByIdmId(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserProfileDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		factory.withTransaction(tx -> {
			var user = new UserProfileEntity();
			user.setId(USER_ID);
			return tx.persist(user);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var subscriber = UniAssertSubscriber.create();
		factory.withoutTransaction(em ->
				sut.requireByIdmId(em, IDM_ID, USER_NAME, false)
		).subscribe().withSubscriber(subscriber);
		subscriber.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).assertFailedWith(NotFoundException.class);

		factory.withTransaction(tx ->
				tx.find(UserProfileEntity.class, USER_ID)
						.invoke(u -> u.setIdmId(IDM_ID))
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var user = factory.withoutTransaction(em ->
				sut.requireByIdmId(em, IDM_ID, USER_NAME, false)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
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
				sut.requireById(em, userId, USER_NAME, false)
		).subscribe().withSubscriber(subscriber);
		subscriber.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).assertFailedWith(NotFoundException.class);

		factory.withTransaction(tx -> {
			var user = new UserProfileEntity();
			user.setId(USER_WITH_EMAIL_ID);
			user.setEmail(EMAIL);
			return tx.persist(user);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var user = factory.withoutTransaction(em ->
				sut.requireById(em, userId, USER_NAME, false)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertEquals(EMAIL, user.getEmail().asString());
	}
}
