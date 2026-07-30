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
import eu.tealhelix.howibuy.dao.jpa.RetailerEntity;
import eu.tealhelix.howibuy.v1.types.impl.GenericRetailerId;
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
	private static final UUID INACTIVE_RETAILER_ID = UUID.fromString("d3f8a1c2-8b41-4a1e-9b3c-6f2e5d4c7a80");

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
	void testWhenRetailerAbsent(Mutiny.SessionFactory sessionFactory) {
		var sut = new RetailerDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var subscriber = factory.withoutTransaction(em ->
				sut.findActiveFlag(em, new GenericRetailerId(RETAILER_ID.toString()))
		).subscribe().withSubscriber(UniAssertSubscriber.create());
		var activeFlag = subscriber.awaitItem(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).getItem();
		assertNull(activeFlag);
	}

	@Test
	@Order(2)
	void testWhenRetailerActive(Mutiny.SessionFactory sessionFactory) {
		var sut = new RetailerDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		persistRetailer(sessionFactory, RETAILER_ID, "Retailer 1", true);

		var subscriber = factory.withoutTransaction(em ->
				sut.findActiveFlag(em, new GenericRetailerId(RETAILER_ID.toString()))
		).subscribe().withSubscriber(UniAssertSubscriber.create());
		var activeFlag = subscriber.awaitItem(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).getItem();
		assertTrue(activeFlag);
	}

	@Test
	@Order(3)
	void testWhenRetailerInactive(Mutiny.SessionFactory sessionFactory) {
		var sut = new RetailerDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		persistRetailer(sessionFactory, INACTIVE_RETAILER_ID, "Retailer 2", false);

		var subscriber = factory.withoutTransaction(em ->
				sut.findActiveFlag(em, new GenericRetailerId(INACTIVE_RETAILER_ID.toString()))
		).subscribe().withSubscriber(UniAssertSubscriber.create());
		var activeFlag = subscriber.awaitItem(Duration.ofSeconds(ASYNC_WAIT_SECONDS)).getItem();
		assertFalse(activeFlag);
	}

	private void persistRetailer(Mutiny.SessionFactory sessionFactory, UUID id, String name, boolean active) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		factory.withTransaction(tx -> {
			var retailer = new RetailerEntity();
			retailer.setId(id);
			retailer.setName(name);
			retailer.setActive(active);
			return tx.persist(retailer);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}
}
