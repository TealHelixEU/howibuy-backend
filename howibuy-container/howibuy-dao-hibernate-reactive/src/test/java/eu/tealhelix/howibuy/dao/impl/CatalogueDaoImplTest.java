package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.tealhelix.howibuy.dao.impl.TestEntities.retailer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public class CatalogueDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final String RETAILER = "6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11";
	private static final String OTHER_RETAILER = "0b6a2f4e-35c4-4d8e-a1a7-5e2c9f8d7b22";
	private static final CatalogueId CATALOGUE_GR = new CatalogueIdImpl("test-gr");
	private static final CatalogueId CATALOGUE_CY = new CatalogueIdImpl("test-cy");
	private static final CatalogueId INACTIVE_CATALOGUE = new CatalogueIdImpl("test-mt");
	private static final CatalogueId OTHER_RETAILERS_CATALOGUE = new CatalogueIdImpl("other-lt");

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

	/**
	 * The Catalogues are inserted with plain JDBC, not JPA: the test is about the mapping of {@code TH_CATALOGUE}, which
	 * rows written through that same mapping could not check.
	 */
	@BeforeAll
	static void setUp(Mutiny.SessionFactory sessionFactory) throws SQLException {
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> tx.persistAll(
						retailer(UUID.fromString(RETAILER), "Test Retailer"),
						retailer(UUID.fromString(OTHER_RETAILER), "Other Retailer")))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement()) {
			s.execute("INSERT INTO TH_CATALOGUE (id, retailer_id, country, primary_language) VALUES "
					+ "('" + CATALOGUE_GR.asString() + "', '" + RETAILER + "', 'GR', 'el'), "
					+ "('" + CATALOGUE_CY.asString() + "', '" + RETAILER + "', 'CY', 'el'), "
					+ "('" + OTHER_RETAILERS_CATALOGUE.asString() + "', '" + OTHER_RETAILER + "', 'LT', 'lt')");
			s.execute("INSERT INTO TH_CATALOGUE (id, retailer_id, country, primary_language, active) VALUES "
					+ "('" + INACTIVE_CATALOGUE.asString() + "', '" + RETAILER + "', 'MT', 'en', false)");
		}
	}

	@Test
	void findsTheRetailerOfEachCatalogue(Mutiny.SessionFactory sessionFactory) {
		var retailers = findRetailers(sessionFactory, Set.of(CATALOGUE_GR, CATALOGUE_CY, OTHER_RETAILERS_CATALOGUE));

		assertEquals(Map.of(
				CATALOGUE_GR, new GenericRetailerId(RETAILER),
				CATALOGUE_CY, new GenericRetailerId(RETAILER),
				OTHER_RETAILERS_CATALOGUE, new GenericRetailerId(OTHER_RETAILER)
		), retailers);
	}

	@Test
	void anUnknownCatalogueIsMissing(Mutiny.SessionFactory sessionFactory) {
		var retailers = findRetailers(sessionFactory, Set.of(CATALOGUE_GR, new CatalogueIdImpl("never-seen")));

		assertEquals(Map.of(CATALOGUE_GR, new GenericRetailerId(RETAILER)), retailers);
	}

	@Test
	void noCataloguesHaveNoRetailers(Mutiny.SessionFactory sessionFactory) {
		assertEquals(Map.of(), findRetailers(sessionFactory, Set.of()));
	}

	@Test
	void anActiveCatalogueOfTheRetailerIsActive(Mutiny.SessionFactory sessionFactory) {
		assertTrue(findActiveFlag(sessionFactory, new GenericRetailerId(RETAILER), CATALOGUE_GR));
	}

	@Test
	void anInactiveCatalogueOfTheRetailerIsInactive(Mutiny.SessionFactory sessionFactory) {
		assertFalse(findActiveFlag(sessionFactory, new GenericRetailerId(RETAILER), INACTIVE_CATALOGUE));
	}

	@Test
	void anotherRetailersCatalogueHasNoActiveFlag(Mutiny.SessionFactory sessionFactory) {
		assertNull(findActiveFlag(sessionFactory, new GenericRetailerId(RETAILER), OTHER_RETAILERS_CATALOGUE));
	}

	@Test
	void anUnknownCatalogueHasNoActiveFlag(Mutiny.SessionFactory sessionFactory) {
		assertNull(findActiveFlag(sessionFactory, new GenericRetailerId(RETAILER), new CatalogueIdImpl("never-seen")));
	}

	private static Boolean findActiveFlag(Mutiny.SessionFactory sessionFactory, RetailerId retailerId, CatalogueId catalogueId) {
		var sut = new CatalogueDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.findActiveFlag(em, retailerId, catalogueId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static Map<CatalogueId, RetailerId> findRetailers(Mutiny.SessionFactory sessionFactory, Set<CatalogueId> catalogueIds) {
		var sut = new CatalogueDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.findRetailers(em, catalogueIds))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}
}
