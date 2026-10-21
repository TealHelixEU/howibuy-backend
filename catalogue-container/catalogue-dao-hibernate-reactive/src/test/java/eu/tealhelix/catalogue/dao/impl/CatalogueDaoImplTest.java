package eu.tealhelix.catalogue.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Set;

import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableCatalogue;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
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
	private static final CatalogueId ACTIVE_CATALOGUE = new CatalogueIdImpl("test-gr");
	private static final CatalogueId INACTIVE_CATALOGUE = new CatalogueIdImpl("test-cy");

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "catalogue.db.changelog.xml", "test");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	/**
	 * Inserts with plain JDBC, not JPA: {@code IN_RETAILER} has no entity, and the test is about the mapping of
	 * {@code IN_CATALOGUE}, which rows written through that same mapping could not check.
	 */
	@BeforeAll
	static void setUp() throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement()) {
			s.execute("INSERT INTO IN_RETAILER (id, name) VALUES ('" + RETAILER + "', 'Test Retailer')");
			s.execute("INSERT INTO IN_CATALOGUE (id, retailer_id, country, primary_language, accepted_languages, adapter, active) VALUES "
					+ "('" + ACTIVE_CATALOGUE.asString() + "', '" + RETAILER + "', 'GR', 'el', '{el,en}', 'test-adapter', TRUE), "
					+ "('" + INACTIVE_CATALOGUE.asString() + "', '" + RETAILER + "', 'CY', 'el', '{el}', 'test-adapter', FALSE)");
		}
	}

	@Test
	void findsACatalogue(Mutiny.SessionFactory sessionFactory) {
		assertEquals(
				ImmutableCatalogue.builder()
						.id(ACTIVE_CATALOGUE)
						.retailerId(new GenericRetailerId(RETAILER))
						.country("GR")
						.primaryLanguage("el")
						.acceptedLanguages(Set.of("el", "en"))
						.adapter("test-adapter")
						.isActive(true)
						.build(),
				find(sessionFactory, ACTIVE_CATALOGUE)
		);
	}

	@Test
	void findsAnInactiveCatalogue(Mutiny.SessionFactory sessionFactory) {
		assertFalse(find(sessionFactory, INACTIVE_CATALOGUE).isActive());
	}

	@Test
	void anUnknownCatalogueIsNotFound(Mutiny.SessionFactory sessionFactory) {
		assertNull(find(sessionFactory, new CatalogueIdImpl("never-seen")));
	}

	private static Catalogue find(Mutiny.SessionFactory sessionFactory, CatalogueId catalogueId) {
		var sut = new CatalogueDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.find(em, catalogueId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}
}
