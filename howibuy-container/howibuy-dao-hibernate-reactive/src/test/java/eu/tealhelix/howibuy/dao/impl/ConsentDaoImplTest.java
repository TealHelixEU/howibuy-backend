package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.jpa.RetailerEntity;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity;
import eu.tealhelix.howibuy.v1.types.impl.GenericRetailerId;
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
public class ConsentDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID USER_ID = UUID.fromString("2e788895-0503-4777-a7bd-24e5d61db5b1");
	private static final UUID RETAILER_ID = UUID.fromString("518cae6a-f2b2-4454-b74d-f2404feab2f5");

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			LiquibaseExtension.withContexts(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "test");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@Test
	@Order(1)
	void testWithoutRecords(Mutiny.SessionFactory sessionFactory) {
		var sut = new ConsentDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		factory.withTransaction(tx -> {
			var retailer = new RetailerEntity();
			retailer.setId(RETAILER_ID);
			retailer.setName("Retailer 1");
			var user = new UserProfileEntity();
			user.setId(USER_ID);
			return tx.persistAll(retailer, user);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var userId = new UserIdImpl(USER_ID.toString());
		var retailerId = new GenericRetailerId(RETAILER_ID.toString());
		Boolean hasConsentedInitially = factory.withTransaction(tx -> sut.hasConsentedToRetailer(tx, userId, retailerId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertFalse(hasConsentedInitially);
	}

	@Test
	@Order(2)
	void testConsent(Mutiny.SessionFactory sessionFactory) {
		var sut = new ConsentDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var userId = new UserIdImpl(USER_ID.toString());
		var retailerId = new GenericRetailerId(RETAILER_ID.toString());
		Boolean initialConsent = factory.withTransaction(tx -> sut.updateConsentToRetailer(tx, userId, retailerId, true))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertNull(initialConsent, "The initial consent does not exist");

		Boolean hasConsentedAfterGrant = factory.withTransaction(tx -> sut.hasConsentedToRetailer(tx, userId, retailerId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertTrue(hasConsentedAfterGrant);

		Boolean previousConsent = factory.withTransaction(tx -> sut.updateConsentToRetailer(tx, userId, retailerId, false))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertTrue(previousConsent);
		Boolean hasConsentedFinally = factory.withTransaction(tx -> sut.hasConsentedToRetailer(tx, userId, retailerId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertFalse(hasConsentedFinally);
	}
}
