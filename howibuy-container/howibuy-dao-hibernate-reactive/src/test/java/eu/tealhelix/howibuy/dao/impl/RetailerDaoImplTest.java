package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;

import eu.tealhelix.howibuy.dao.jpa.RetailerEntity;
import eu.tealhelix.howibuy.v1.types.impl.GenericRetailerId;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class RetailerDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID RETAILER_ID = UUID.fromString("518cae6a-f2b2-4454-b74d-f2404feab2f5");

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

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
	void testWhenRetailerAbsent(Mutiny.SessionFactory sessionFactory) {
		var sut = new RetailerDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var subscriber = factory.withoutTransaction(em ->
				sut.exists(em, new GenericRetailerId(RETAILER_ID.toString()))
		).subscribe().withSubscriber(UniAssertSubscriber.create());
		var exists = subscriber.awaitItem(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).getItem();
		assertFalse(exists);
	}

	@Test
	@Order(2)
	void testWhenRetailerPresent(Mutiny.SessionFactory sessionFactory) {
		var sut = new RetailerDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		factory.withTransaction(tx -> {
			var retailer = new RetailerEntity();
			retailer.setId(RETAILER_ID);
			retailer.setName("Retailer 1");
			return tx.persist(retailer);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var subscriber = factory.withoutTransaction(em ->
				sut.exists(em, new GenericRetailerId(RETAILER_ID.toString()))
		).subscribe().withSubscriber(UniAssertSubscriber.create());
		var exists = subscriber.awaitItem(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).getItem();
		assertTrue(exists);
	}
}
