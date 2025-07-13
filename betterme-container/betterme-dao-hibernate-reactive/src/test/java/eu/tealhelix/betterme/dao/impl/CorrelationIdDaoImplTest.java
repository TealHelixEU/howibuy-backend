package eu.tealhelix.betterme.dao.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.UUID;

import eu.tealhelix.betterme.dao.jpa.CorrelationIdEntity;
import eu.tealhelix.betterme.dao.jpa.RetailerEntity;
import eu.tealhelix.betterme.dao.jpa.UserProfileEntity;
import eu.tealhelix.betterme.v1.types.impl.GenericRetailerId;
import eu.tealhelix.common.dao.EntityNotFoundException;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class CorrelationIdDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID USER_ID = UUID.fromString("2e788895-0503-4777-a7bd-24e5d61db5b1");
	private static final UUID USER_EXTERNAL_ID = UUID.fromString("2e788895-0503-4777-a7bd-24e5d61d0000");
	private static final UUID RETAILER_ID = UUID.fromString("518cae6a-f2b2-4454-b74d-f2404feab2f5");
	private static final String CORRELATION_ID = "abc";

	@Container
	private static final PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine").asCompatibleSubstituteFor("postgres"));

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@Test
	@Order(1)
	void testWithoutRecords(Mutiny.SessionFactory sessionFactory) {
		var sut = new CorrelationIdDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		factory.withTransaction(tx -> {
			var retailer = new RetailerEntity();
			retailer.setId(RETAILER_ID);
			retailer.setName("Retailer 1");
			var user = new UserProfileEntity();
			user.setId(USER_ID);
			user.setExternalId(USER_EXTERNAL_ID.toString());
			return tx.persistAll(retailer, user);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var subscriber = UniAssertSubscriber.create();
		factory.withTransaction(tx ->
				sut.requireByRetailerAndCorrelationId(tx, new GenericRetailerId(RETAILER_ID.toString()), CORRELATION_ID)
		).subscribe().withSubscriber(subscriber);
		subscriber.awaitFailure(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).assertFailedWith(EntityNotFoundException.class);
	}

	@Test
	@Order(2)
	void testCreation(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		// TODO Refactor when we have the CorrelationIdEntity creation method
		factory.withTransaction(tx -> {
			var correlationIdEntity = new CorrelationIdEntity();
			correlationIdEntity.setRetailer(tx.getReference(RetailerEntity.class, RETAILER_ID));
			correlationIdEntity.setCorrelationId(CORRELATION_ID);
			correlationIdEntity.setUser(tx.getReference(UserProfileEntity.class, USER_ID));
			return tx.persistAll(correlationIdEntity);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	@Test
	@Order(3)
	void testWithRecords(Mutiny.SessionFactory sessionFactory) {
		var sut = new CorrelationIdDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var user = factory.withTransaction(tx ->
				sut.requireByRetailerAndCorrelationId(tx, new GenericRetailerId(RETAILER_ID.toString()), CORRELATION_ID)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertNotNull(user);
		assertEquals(USER_ID, user.getId().asUuid());
	}
}
