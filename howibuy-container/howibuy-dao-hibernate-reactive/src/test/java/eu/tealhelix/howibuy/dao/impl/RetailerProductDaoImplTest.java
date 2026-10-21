package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import eu.tealhelix.catalogue.v1.model.ImmutableListing;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.Listing;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Applying Product Changes makes each Retailer Product exactly what its change describes, in the rows of the product, its
 * names and its Listings. State is asserted directly over JDBC, because the DAO has no read method yet.
 */
@Testcontainers
public class RetailerProductDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final String RETAILER = "6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11";
	private static final String OTHER_RETAILER = "0b6a2f4e-35c4-4d8e-a1a7-5e2c9f8d7b22";
	private static final String CATALOGUE_GR = "test-gr";
	private static final String CATALOGUE_CY = "test-cy";
	private static final String CATALOGUE_MT = "test-mt";
	private static final String OTHER_RETAILERS_CATALOGUE = "other-lt";

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

	@BeforeEach
	void setUp() throws SQLException {
		execute(
				"DELETE FROM TH_LISTING",
				"DELETE FROM TH_RETAILER_PRODUCT_NAME",
				"DELETE FROM TH_RETAILER_PRODUCT",
				"INSERT INTO TH_RETAILER (id, name) VALUES ('" + RETAILER + "', 'Test Retailer'), ('" + OTHER_RETAILER + "', 'Other Retailer') ON CONFLICT (id) DO NOTHING",
				"INSERT INTO TH_CATALOGUE (id, retailer_id, country, primary_language) VALUES "
						+ "('" + CATALOGUE_GR + "', '" + RETAILER + "', 'GR', 'el'), "
						+ "('" + CATALOGUE_CY + "', '" + RETAILER + "', 'CY', 'el'), "
						+ "('" + CATALOGUE_MT + "', '" + RETAILER + "', 'MT', 'en'), "
						+ "('" + OTHER_RETAILERS_CATALOGUE + "', '" + OTHER_RETAILER + "', 'LT', 'lt') ON CONFLICT (id) DO NOTHING"
		);
	}

	@Test
	void createsAProductWithItsContentNamesAndListings(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory, yoghurt(1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt"), listing(CATALOGUE_GR, "1.99", true), listing(CATALOGUE_CY, "2.0950", false)));

		assertEquals(1, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"));
		assertTrue(bool("SELECT retailer_id = '" + RETAILER + "' FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"), "the Retailer stored");
		assertTrue(bool("SELECT characteristics = '{\"fat\": \"2%\"}'::jsonb FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"), "the characteristics stored");
		assertTrue(bool("SELECT tags = ARRAY['dairy']::varchar[] FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"), "the tags stored");
		assertEquals("h1", text("SELECT input_hash FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"));
		assertEquals(1, scalar("SELECT change_sequence FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"));
		assertEquals(Map.of("el", "Γιαούρτι", "en", "Yoghurt"), names(RETAILER, "yoghurt"));
		assertEquals(Map.of(CATALOGUE_GR, "1.9900 EUR active", CATALOGUE_CY, "2.0950 EUR inactive"), listings(RETAILER, "yoghurt"));
	}

	@Test
	void updatesTheSameRowInPlace(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory, yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), listing(CATALOGUE_GR, "1.99", true)));
		var id = text("SELECT id::text FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'");

		apply(sessionFactory, product(RETAILER, "yoghurt", 4, "h4", Map.of("el", "Γιαούρτι"), Map.of("fat", "0%"), List.of("dairy", "light"), listing(CATALOGUE_GR, "1.79", true)));

		assertEquals(1, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"));
		assertEquals(id, text("SELECT id::text FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"), "the product keeps its id");
		assertTrue(bool("SELECT characteristics = '{\"fat\": \"0%\"}'::jsonb AND tags = ARRAY['dairy', 'light']::varchar[] FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"), "the content replaced");
		assertEquals("h4", text("SELECT input_hash FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"));
		assertEquals(4, scalar("SELECT change_sequence FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"));
		assertEquals(Map.of(CATALOGUE_GR, "1.7900 EUR active"), listings(RETAILER, "yoghurt"));
	}

	@Test
	void namesBecomeTheLanguagesOfTheChange(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory, yoghurt(1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt"), listing(CATALOGUE_GR, "1.99", true)));

		apply(sessionFactory, yoghurt(2, "h2", Map.of("el", "Γιαούρτι στραγγιστό", "de", "Joghurt"), listing(CATALOGUE_GR, "1.99", true)));

		assertEquals(Map.of("el", "Γιαούρτι στραγγιστό", "de", "Joghurt"), names(RETAILER, "yoghurt"), "one name replaced, one removed and one added");
	}

	@Test
	void listingsBecomeTheListingsOfTheChange(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory, yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), listing(CATALOGUE_GR, "1.99", true), listing(CATALOGUE_CY, "2.09", true)));

		apply(sessionFactory, yoghurt(2, "h1", Map.of("el", "Γιαούρτι"), listing(CATALOGUE_GR, "1.99", false), listing(CATALOGUE_MT, "2.19", true)));

		assertEquals(Map.of(CATALOGUE_GR, "1.9900 EUR inactive", CATALOGUE_MT, "2.1900 EUR active"), listings(RETAILER, "yoghurt"), "one Listing made inactive, one removed and one added");
	}

	@Test
	void aRemovedProductIsDeletedWithItsNamesAndListings(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory, yoghurt(1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt"), listing(CATALOGUE_GR, "1.99", true), listing(CATALOGUE_CY, "2.09", true)));

		apply(sessionFactory, removed(RETAILER, "yoghurt", 2));

		assertEquals(0, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"));
		assertEquals(0, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT_NAME"));
		assertEquals(0, scalar("SELECT count(*) FROM TH_LISTING"));
	}

	@Test
	void removingAnUnknownProductChangesNothing(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory, yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), listing(CATALOGUE_GR, "1.99", true)));

		apply(sessionFactory, removed(RETAILER, "never-seen", 2));

		assertEquals(1, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"));
		assertEquals(Map.of("el", "Γιαούρτι"), names(RETAILER, "yoghurt"));
	}

	@Test
	void theLastOfTwoChangesOfTheSameProductWins(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory,
				yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), listing(CATALOGUE_GR, "1.99", true)),
				yoghurt(2, "h2", Map.of("el", "Γιαούρτι στραγγιστό"), listing(CATALOGUE_CY, "2.09", true)));

		assertEquals(1, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"), "the second change updated the product the first one created");
		assertEquals("h2", text("SELECT input_hash FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"));
		assertEquals(Map.of("el", "Γιαούρτι στραγγιστό"), names(RETAILER, "yoghurt"));
		assertEquals(Map.of(CATALOGUE_CY, "2.0900 EUR active"), listings(RETAILER, "yoghurt"));
	}

	@Test
	void theSameProductKeyOfTwoRetailersNamesTwoProducts(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory,
				yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), listing(CATALOGUE_GR, "1.99", true)),
				product(OTHER_RETAILER, "yoghurt", 2, "h2", Map.of("lt", "Jogurtas"), Map.of(), List.of(), listing(OTHER_RETAILERS_CATALOGUE, "0.99", true)));

		apply(sessionFactory, removed(OTHER_RETAILER, "yoghurt", 3));

		assertEquals(1, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"), "only the other Retailer's product was removed");
		assertEquals(Map.of("el", "Γιαούρτι"), names(RETAILER, "yoghurt"));
	}

	/**
	 * The products are read by Retailer and by product key separately, so a product of one changed Retailer with the
	 * product key of another changed product is read too. It must come out untouched.
	 */
	@Test
	void aProductNoChangeIsAboutIsLeftAlone(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory,
				yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), listing(CATALOGUE_GR, "1.99", true)),
				product(RETAILER, "feta", 2, "h2", Map.of("el", "Φέτα"), Map.of(), List.of(), listing(CATALOGUE_GR, "4.50", true)),
				product(OTHER_RETAILER, "feta", 3, "h3", Map.of("lt", "Feta"), Map.of(), List.of(), listing(OTHER_RETAILERS_CATALOGUE, "3.50", true)));

		apply(sessionFactory,
				yoghurt(4, "h4", Map.of("el", "Γιαούρτι στραγγιστό"), listing(CATALOGUE_GR, "1.79", true)),
				removed(OTHER_RETAILER, "feta", 5));

		assertEquals(Map.of("el", "Φέτα"), names(RETAILER, "feta"));
		assertEquals(Map.of(CATALOGUE_GR, "4.5000 EUR active"), listings(RETAILER, "feta"));
		assertEquals(2, scalar("SELECT change_sequence FROM TH_RETAILER_PRODUCT WHERE retailer_id = '" + RETAILER + "' AND product_key = 'feta'"));
	}

	/**
	 * As many changes as the largest page of the change cursor of the Ingestion.
	 */
	@Test
	void appliesManyChangesAtOnce(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory, IntStream.rangeClosed(1, 1000)
				.mapToObj(i -> product(RETAILER, "product-" + i, i, "h" + i, Map.of("el", "Προϊόν " + i), Map.of(), List.of(), listing(CATALOGUE_GR, "1.99", true)))
				.toArray(ProductChange[]::new));

		assertEquals(1000, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"));
		assertEquals(1000, scalar("SELECT count(*) FROM TH_LISTING"));
	}

	private static void apply(Mutiny.SessionFactory sessionFactory, ProductChange... changes) {
		var sut = new RetailerProductDaoImpl();
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.apply(tx, List.of(changes)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static ProductChange yoghurt(long changeSequence, String inputHash, Map<String, String> names, Listing... listings) {
		return product(RETAILER, "yoghurt", changeSequence, inputHash, names, Map.of("fat", "2%"), List.of("dairy"), listings);
	}

	private static ProductChange product(String retailerId, String productKey, long changeSequence, String inputHash, Map<String, String> names, Map<String, String> characteristics, List<String> tags, Listing... listings) {
		return ImmutableProductChange.builder()
				.retailerId(new GenericRetailerId(retailerId))
				.productKey(new ProductKeyImpl(productKey))
				.changeSequence(changeSequence)
				.isRemoved(false)
				.inputHash(inputHash)
				.names(names)
				.characteristics(characteristics)
				.tags(tags)
				.addListings(listings)
				.build();
	}

	private static ProductChange removed(String retailerId, String productKey, long changeSequence) {
		return ImmutableProductChange.builder()
				.retailerId(new GenericRetailerId(retailerId))
				.productKey(new ProductKeyImpl(productKey))
				.changeSequence(changeSequence)
				.isRemoved(true)
				.build();
	}

	private static Listing listing(String catalogueId, String price, boolean active) {
		return ImmutableListing.builder()
				.catalogueId(new CatalogueIdImpl(catalogueId))
				.price(new BigDecimal(price))
				.currency(Currency.getInstance("EUR"))
				.isActive(active)
				.build();
	}

	private static Map<String, String> names(String retailerId, String productKey) throws SQLException {
		return rows("SELECT n.lang, n.name FROM TH_RETAILER_PRODUCT_NAME n JOIN TH_RETAILER_PRODUCT p ON p.id = n.retailer_product_id "
				+ "WHERE p.retailer_id = '" + retailerId + "' AND p.product_key = '" + productKey + "'");
	}

	private static Map<String, String> listings(String retailerId, String productKey) throws SQLException {
		return rows("SELECT l.catalogue_id, l.price || ' ' || l.currency || ' ' || CASE WHEN l.active THEN 'active' ELSE 'inactive' END "
				+ "FROM TH_LISTING l JOIN TH_RETAILER_PRODUCT p ON p.id = l.retailer_product_id "
				+ "WHERE p.retailer_id = '" + retailerId + "' AND p.product_key = '" + productKey + "'");
	}

	private static void execute(String... statements) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement()) {
			for (var statement : statements) {
				s.execute(statement);
			}
		}
	}

	private static Map<String, String> rows(String sql) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			var result = new TreeMap<String, String>();
			while (rs.next()) {
				result.put(rs.getString(1), rs.getString(2));
			}
			return result;
		}
	}

	private static long scalar(String sql) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}

	private static String text(String sql) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

	private static boolean bool(String sql) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getBoolean(1);
		}
	}
}
