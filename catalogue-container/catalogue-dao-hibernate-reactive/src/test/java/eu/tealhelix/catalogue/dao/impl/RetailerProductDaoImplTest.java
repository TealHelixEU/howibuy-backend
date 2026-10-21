package eu.tealhelix.catalogue.dao.impl;

import static eu.tealhelix.catalogue.dao.impl.TestEntities.catalogue;
import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.tealhelix.catalogue.dao.jpa.IngestionRunEntity;
import eu.tealhelix.catalogue.v1.model.ImmutableListing;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.Listing;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
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
public class RetailerProductDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final RetailerId RETAILER = new GenericRetailerId("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11");
	private static final RetailerId OTHER_RETAILER = new GenericRetailerId("0b7e2d4c-1a3f-4e5b-8c6d-9f0a1b2c3d4e");
	private static final CatalogueId CATALOGUE = new CatalogueIdImpl("test-lt");
	private static final CatalogueId OTHER_CATALOGUE = new CatalogueIdImpl("test-lv");
	private static final CatalogueId OTHER_RETAILERS_CATALOGUE = new CatalogueIdImpl("other-lt");
	private static final IngestionRunId RUN = new IngestionRunIdImpl("5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e01");
	private static final IngestionRunId LATER_RUN = new IngestionRunIdImpl("5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e02");
	private static final IngestionRunId OTHER_CATALOGUE_RUN = new IngestionRunIdImpl("5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e03");
	private static final IngestionRunId OTHER_RETAILERS_RUN = new IngestionRunIdImpl("5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e04");
	private static final LocalDateTime SEEN_AT = LocalDateTime.of(2026, 10, 21, 10, 0, 0);
	private static final LocalDateTime LATER = LocalDateTime.of(2026, 10, 22, 10, 0, 0);
	private static final Currency EUR = Currency.getInstance("EUR");

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
	 * The Retailers are inserted with plain JDBC, not JPA: {@code IN_RETAILER} has no entity.
	 */
	@BeforeAll
	static void setUp(Mutiny.SessionFactory sessionFactory) throws SQLException {
		execute("INSERT INTO IN_RETAILER (id, name) VALUES ('" + RETAILER.asString() + "', 'Test Retailer'), ('" + OTHER_RETAILER.asString() + "', 'Other Retailer')");
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> tx.persistAll(
								catalogue(CATALOGUE, RETAILER, "LT", "lt", "en"),
								catalogue(OTHER_CATALOGUE, RETAILER, "LV", "lv", "en"),
								catalogue(OTHER_RETAILERS_CATALOGUE, OTHER_RETAILER, "LT", "lt"))
						.chain(() -> tx.persistAll(
								ingestionRun(RUN, CATALOGUE),
								ingestionRun(LATER_RUN, CATALOGUE),
								ingestionRun(OTHER_CATALOGUE_RUN, OTHER_CATALOGUE),
								ingestionRun(OTHER_RETAILERS_RUN, OTHER_RETAILERS_CATALOGUE))))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	@Test
	void readsBackASavedProduct(Mutiny.SessionFactory sessionFactory) {
		var product = product(RETAILER, newKey(), 7, listing(CATALOGUE, "1.29", true));

		save(sessionFactory, CATALOGUE, RUN, SEEN_AT, product);

		assertEquals(Map.of(product.getProductKey(), product), findProducts(sessionFactory, RETAILER, product.getProductKey()));
	}

	@Test
	void replacesTheStateOfASavedProduct(Mutiny.SessionFactory sessionFactory) {
		var key = newKey();
		save(sessionFactory, CATALOGUE, RUN, SEEN_AT, product(RETAILER, key, 7, listing(CATALOGUE, "1.29", true)));
		var changed = ImmutableProductChange.builder()
				.from(product(RETAILER, key, 8, listing(CATALOGUE, "1.19", false), listing(OTHER_CATALOGUE, "1.49", true)))
				.names(Map.of("lt", "Pienas 2.5%", "lv", "Piens"))
				.characteristics(Map.of("volume", "1l"))
				.tags(List.of("organic"))
				.inputHash("b".repeat(64))
				.isRemoved(true)
				.build();

		save(sessionFactory, OTHER_CATALOGUE, OTHER_CATALOGUE_RUN, LATER, changed);

		assertEquals(Map.of(key, changed), findProducts(sessionFactory, RETAILER, key));
	}

	@Test
	void findsOnlyTheProductsAskedForOfTheRetailer(Mutiny.SessionFactory sessionFactory) {
		var key = newKey();
		var otherKey = newKey();
		var product = product(RETAILER, key, 7, listing(CATALOGUE, "1.29", true));
		save(sessionFactory, CATALOGUE, RUN, SEEN_AT, product, product(RETAILER, otherKey, 7, listing(CATALOGUE, "1.29", true)));
		save(sessionFactory, OTHER_RETAILERS_CATALOGUE, OTHER_RETAILERS_RUN, SEEN_AT, product(OTHER_RETAILER, key, 7, listing(OTHER_RETAILERS_CATALOGUE, "1.29", true)));

		assertEquals(Map.of(key, product), findProducts(sessionFactory, RETAILER, key, newKey()));
	}

	@Test
	void findsNoProductsForNoKeys(Mutiny.SessionFactory sessionFactory) {
		assertEquals(Map.of(), findProducts(sessionFactory, RETAILER));
	}

	@Test
	void recordsTheListingsOfTheCatalogueAsSeenByTheRun(Mutiny.SessionFactory sessionFactory) throws SQLException {
		var key = newKey();
		save(sessionFactory, CATALOGUE, RUN, SEEN_AT, product(RETAILER, key, 7, listing(CATALOGUE, "1.29", true)));
		save(sessionFactory, OTHER_CATALOGUE, OTHER_CATALOGUE_RUN, SEEN_AT, product(RETAILER, key, 8, listing(CATALOGUE, "1.29", true), listing(OTHER_CATALOGUE, "1.49", true)));

		save(sessionFactory, CATALOGUE, LATER_RUN, LATER, product(RETAILER, key, 8, listing(CATALOGUE, "1.29", true), listing(OTHER_CATALOGUE, "1.49", true)));

		assertEquals(
				List.of(
						List.of(CATALOGUE.asString(), LATER.toString(), LATER_RUN.asString()),
						List.of(OTHER_CATALOGUE.asString(), SEEN_AT.toString(), OTHER_CATALOGUE_RUN.asString())
				),
				findSeen(key)
		);
	}

	@Test
	void takesAscendingValuesOfTheChangeSequence(Mutiny.SessionFactory sessionFactory) {
		var first = takeChangeSequences(sessionFactory, 3);
		var second = takeChangeSequences(sessionFactory, 2);

		assertEquals(3, first.size());
		assertEquals(2, second.size());
		var all = new ArrayList<>(first);
		all.addAll(second);
		for (int i = 1; i < all.size(); i++) {
			assertTrue(all.get(i) > all.get(i - 1), "Not ascending: " + all);
		}
	}

	@Test
	void takesNoValuesOfTheChangeSequence(Mutiny.SessionFactory sessionFactory) {
		assertEquals(List.of(), takeChangeSequences(sessionFactory, 0));
	}

	private static IngestionRunEntity ingestionRun(IngestionRunId runId, CatalogueId catalogueId) {
		return TestEntities.ingestionRun(runId, catalogueId, IngestionRunStatus.PROCESSING, LocalDateTime.of(2026, 10, 21, 9, 0, 0));
	}

	private static ProductKey newKey() {
		return new ProductKeyImpl("P-" + UUID.randomUUID());
	}

	private static ProductChange product(RetailerId retailerId, ProductKey key, long changeSequence, Listing... listings) {
		return ImmutableProductChange.builder()
				.retailerId(retailerId)
				.productKey(key)
				.changeSequence(changeSequence)
				.isRemoved(false)
				.inputHash("a".repeat(64))
				.names(Map.of("lt", "Pienas", "en", "Milk"))
				.characteristics(Map.of("fat", "2.5%"))
				.tags(List.of("dairy", "local"))
				.addListings(listings)
				.build();
	}

	/**
	 * The price has the scale of its column, so that the product reads back equal.
	 */
	private static Listing listing(CatalogueId catalogueId, String price, boolean active) {
		return ImmutableListing.builder().catalogueId(catalogueId).price(new BigDecimal(price).setScale(4)).currency(EUR).isActive(active).build();
	}

	private static void save(Mutiny.SessionFactory sessionFactory, CatalogueId catalogueId, IngestionRunId runId, LocalDateTime seenAt, ProductChange... products) {
		var sut = new RetailerProductDaoImpl();
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.save(tx, catalogueId, runId, seenAt, List.of(products)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static Map<ProductKey, ProductChange> findProducts(Mutiny.SessionFactory sessionFactory, RetailerId retailerId, ProductKey... keys) {
		var sut = new RetailerProductDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.findProducts(em, retailerId, Set.of(keys)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static List<Long> takeChangeSequences(Mutiny.SessionFactory sessionFactory, int count) {
		var sut = new RetailerProductDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.takeChangeSequences(tx, count))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	/**
	 * Reads with plain JDBC, not JPA: the test is about the mapping of the columns, which reading through that same
	 * mapping could not check.
	 */
	private static List<List<String>> findSeen(ProductKey key) throws SQLException {
		var seen = new ArrayList<List<String>>();
		try (
				var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
				var s = c.prepareStatement("""
						SELECT l.catalogue_id, l.last_seen_at, l.last_seen_run_id
						FROM IN_LISTING l JOIN IN_RETAILER_PRODUCT p ON p.id = l.retailer_product_id
						WHERE p.product_key = ?
						ORDER BY l.catalogue_id
						""")
		) {
			s.setString(1, key.asString());
			try (var rs = s.executeQuery()) {
				while (rs.next()) {
					seen.add(List.of(rs.getString(1), rs.getTimestamp(2).toLocalDateTime().toString(), rs.getString(3)));
				}
			}
		}
		return seen;
	}

	private static void execute(String sql) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement()) {
			s.execute(sql);
		}
	}
}
