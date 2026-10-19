package eu.tealhelix.howibuy.tests;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.common.test.quarkus.PostgresTestResource;
import eu.tealhelix.common.v1.model.Role;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.common.web.authentication.jwt.TokenHelper;
import eu.tealhelix.common.web.authentication.jwt.TokenHelperImpl;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Applying pages of Product Changes against a real database: each change is the complete current state of a Retailer
 * Product, so its names and Listings become exactly what the change carries, and a page applies only on top of the
 * cursor it starts from. State is asserted directly over JDBC. JWT decoding is faked (a stubbed {@link TokenHelper});
 * the real tokens of the sync service account are exercised by {@code CatalogueSyncWorkflowTest}.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, initArgs = @ResourceArg(name = "contexts", value = "appdata"))
public class CatalogueChangesTest {
	private static final String BASE = "/api/howibuy/v1/catalogue-changes";

	private static final String RETAILER_ID = "6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11";
	private static final String OTHER_RETAILER_ID = "0b6a2f4e-35c4-4d8e-a1a7-5e2c9f8d7b22";
	private static final String CATALOGUE_GR = "test-gr";
	private static final String CATALOGUE_CY = "test-cy";
	private static final String OTHER_RETAILERS_CATALOGUE = "other-lt";

	private static final User CATALOGUE_SYNC = new UserImpl(new UserIdImpl("1d0c6b0e-3b5f-4d2a-8f7e-2a9c4b6d8e33"), "catalogue-sync", null, false, false, Set.of(Role.CATALOGUE_SYNC));
	private static final User RETAILER = new UserImpl(new UserIdImpl(RETAILER_ID), "test-retailer", null, false, false, Set.of(Role.RETAILER));
	private static final User OPERATOR = new UserImpl(new UserIdImpl("4e2d8c1a-7b3f-4a6e-9c5d-3f1b7a9e2c44"), "operator", null, false, true, Set.of(Role.OPERATOR));
	private static final Map<String, User> USERS_BY_TOKEN = Map.of("sync", CATALOGUE_SYNC, "retailer", RETAILER, "operator", OPERATOR);

	@Inject
	DataSource dataSource;

	@BeforeEach
	void setUp() throws SQLException {
		QuarkusMock.installMockForType(new StubTokenHelper(), TokenHelper.class);
		try (var c = dataSource.getConnection(); var s = c.createStatement()) {
			s.execute("DELETE FROM TH_LISTING");
			s.execute("DELETE FROM TH_RETAILER_PRODUCT_NAME");
			s.execute("DELETE FROM TH_RETAILER_PRODUCT");
			s.execute("INSERT INTO TH_RETAILER (id, name) VALUES ('" + RETAILER_ID + "', 'Test Retailer'), ('" + OTHER_RETAILER_ID + "', 'Other Retailer') ON CONFLICT (id) DO NOTHING");
			s.execute("INSERT INTO TH_CATALOGUE (id, retailer_id, country, primary_language) VALUES "
					+ "('" + CATALOGUE_GR + "', '" + RETAILER_ID + "', 'GR', 'el'), "
					+ "('" + CATALOGUE_CY + "', '" + RETAILER_ID + "', 'CY', 'el'), "
					+ "('" + OTHER_RETAILERS_CATALOGUE + "', '" + OTHER_RETAILER_ID + "', 'LT', 'lt') ON CONFLICT (id) DO NOTHING");
			s.execute("UPDATE TH_CATALOGUE_CHANGE_CURSOR SET last_sequence = 0");
		}
	}

	@Test
	void theCursorIsZeroBeforeAnythingIsApplied() {
		as("sync").get(BASE + "/cursor")
				.then()
				.statusCode(200)
				.contentType(JSON)
				.body("lastSequence", equalTo(0));
	}

	@Test
	void aFirstPageStoresTheProductsWithTheirNamesAndListings() throws SQLException {
		postPage(0, 2, List.of(
				yoghurt(1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt"), List.of(listing(CATALOGUE_GR, "1.99", true), listing(CATALOGUE_CY, "2.0950", true))),
				product("feta", 2, "h2", Map.of("el", "Φέτα"), Map.of(), List.of(), List.of(listing(CATALOGUE_GR, "4.50", true)))
		), 204);

		assertEquals(2, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"), "both products stored");
		assertEquals(Map.of("el", "Γιαούρτι", "en", "Yoghurt"), names("yoghurt"), "the names of every language stored");
		assertEquals(Map.of(CATALOGUE_GR, "1.9900 EUR active", CATALOGUE_CY, "2.0950 EUR active"), listings("yoghurt"), "a Listing per Catalogue");
		assertTrue(bool("SELECT characteristics = '{\"fat\": \"2%\"}'::jsonb AND tags = ARRAY['dairy']::varchar[] FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"), "characteristics and tags stored");
		assertEquals("h1", text("SELECT input_hash FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"), "the input hash stored");
		assertEquals(1, scalar("SELECT change_sequence FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"), "the change sequence stored");
		assertEquals(2, cursor(), "the cursor advanced to the end of the page");
	}

	@Test
	void anUpdatePageReplacesTheContentAndThePrice() throws SQLException {
		postPage(0, 1, List.of(yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), List.of(listing(CATALOGUE_GR, "1.99", true)))), 204);

		postPage(1, 5, List.of(product("yoghurt", 5, "h5", Map.of("el", "Γιαούρτι"), Map.of("fat", "0%"), List.of("dairy", "light"), List.of(listing(CATALOGUE_GR, "1.79", true)))), 204);

		assertEquals(1, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"), "the product was updated, not added again");
		assertTrue(bool("SELECT characteristics = '{\"fat\": \"0%\"}'::jsonb AND tags = ARRAY['dairy', 'light']::varchar[] FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"), "characteristics and tags replaced");
		assertEquals("h5", text("SELECT input_hash FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"), "the input hash replaced");
		assertEquals(5, scalar("SELECT change_sequence FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"), "the change sequence replaced");
		assertEquals(Map.of(CATALOGUE_GR, "1.7900 EUR active"), listings("yoghurt"), "the price replaced");
		assertEquals(5, cursor(), "the cursor advanced to the end of the second page");
	}

	@Test
	void theNameOfOneLanguageIsReplacedWhileTheOthersAreKept() throws SQLException {
		postPage(0, 1, List.of(yoghurt(1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt"), List.of(listing(CATALOGUE_GR, "1.99", true)))), 204);

		postPage(1, 2, List.of(yoghurt(2, "h2", Map.of("el", "Γιαούρτι στραγγιστό", "en", "Yoghurt"), List.of(listing(CATALOGUE_GR, "1.99", true)))), 204);

		assertEquals(Map.of("el", "Γιαούρτι στραγγιστό", "en", "Yoghurt"), names("yoghurt"));
	}

	@Test
	void aLanguageMissingFromTheChangeIsRemoved() throws SQLException {
		postPage(0, 1, List.of(yoghurt(1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt"), List.of(listing(CATALOGUE_GR, "1.99", true)))), 204);

		postPage(1, 2, List.of(yoghurt(2, "h2", Map.of("el", "Γιαούρτι"), List.of(listing(CATALOGUE_GR, "1.99", true)))), 204);

		assertEquals(Map.of("el", "Γιαούρτι"), names("yoghurt"));
	}

	@Test
	void aListingBecomesInactive() throws SQLException {
		postPage(0, 1, List.of(yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), List.of(listing(CATALOGUE_GR, "1.99", true), listing(CATALOGUE_CY, "2.09", true)))), 204);

		postPage(1, 2, List.of(yoghurt(2, "h1", Map.of("el", "Γιαούρτι"), List.of(listing(CATALOGUE_GR, "1.99", false), listing(CATALOGUE_CY, "2.09", true)))), 204);

		assertEquals(Map.of(CATALOGUE_GR, "1.9900 EUR inactive", CATALOGUE_CY, "2.0900 EUR active"), listings("yoghurt"));
	}

	@Test
	void aListingMissingFromTheChangeIsRemoved() throws SQLException {
		postPage(0, 1, List.of(yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), List.of(listing(CATALOGUE_GR, "1.99", true), listing(CATALOGUE_CY, "2.09", false)))), 204);

		postPage(1, 2, List.of(yoghurt(2, "h1", Map.of("el", "Γιαούρτι"), List.of(listing(CATALOGUE_GR, "1.99", true)))), 204);

		assertEquals(Map.of(CATALOGUE_GR, "1.9900 EUR active"), listings("yoghurt"));
	}

	@Test
	void aTombstoneDeletesTheProduct() throws SQLException {
		postPage(0, 1, List.of(yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), List.of(listing(CATALOGUE_GR, "1.99", true)))), 204);

		postPage(1, 2, List.of(tombstone("yoghurt", 2)), 204);

		assertEquals(0, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"), "the product deleted");
		assertEquals(0, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT_NAME"), "its names deleted");
		assertEquals(0, scalar("SELECT count(*) FROM TH_LISTING"), "its Listings deleted");
		assertEquals(2, cursor());
	}

	@Test
	void aTombstoneOfAnUnknownProductChangesNothingButTheCursor() throws SQLException {
		postPage(0, 3, List.of(tombstone("never-seen", 3)), 204);

		assertEquals(0, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"));
		assertEquals(3, cursor());
	}

	@Test
	void aPageNotStartingAtTheCursorIsRefusedAndNothingIsApplied() throws SQLException {
		postPage(0, 1, List.of(yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), List.of(listing(CATALOGUE_GR, "1.99", true)))), 204);

		postPage(0, 2, List.of(yoghurt(2, "h2", Map.of("el", "Άλλο"), List.of(listing(CATALOGUE_GR, "9.99", true)))), 409);

		assertEquals(Map.of("el", "Γιαούρτι"), names("yoghurt"), "the refused page changed no name");
		assertEquals(Map.of(CATALOGUE_GR, "1.9900 EUR active"), listings("yoghurt"), "the refused page changed no price");
		assertEquals(1, cursor(), "the cursor stayed");
	}

	@Test
	void applyingTheSamePageTwiceLeavesTheSameState() throws SQLException {
		var page = List.of(
				yoghurt(1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt"), List.of(listing(CATALOGUE_GR, "1.99", true), listing(CATALOGUE_CY, "2.09", false))),
				tombstone("never-seen", 2)
		);
		postPage(0, 2, page, 204);
		var namesAfterFirst = names("yoghurt");
		var listingsAfterFirst = listings("yoghurt");
		var productIdAfterFirst = text("SELECT id::text FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'");

		resetCursor();
		postPage(0, 2, page, 204);

		assertEquals(1, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"));
		assertEquals(productIdAfterFirst, text("SELECT id::text FROM TH_RETAILER_PRODUCT WHERE product_key = 'yoghurt'"), "the same row was updated");
		assertEquals(namesAfterFirst, names("yoghurt"));
		assertEquals(listingsAfterFirst, listings("yoghurt"));
		assertEquals(2, cursor());
	}

	@Test
	void aListingInACatalogueOfAnotherRetailerIsRefusedAndNothingIsApplied() throws SQLException {
		postPage(0, 2, List.of(
				yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), List.of(listing(CATALOGUE_GR, "1.99", true))),
				yoghurt(2, "h1", Map.of("el", "Γιαούρτι"), List.of(listing(OTHER_RETAILERS_CATALOGUE, "1.99", true)))
		), 400);

		assertEquals(0, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"));
		assertEquals(0, cursor());
	}

	@Test
	void aListingInAnUnknownCatalogueIsRefused() throws SQLException {
		postPage(0, 1, List.of(yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), List.of(listing("no-such-catalogue", "1.99", true)))), 400);

		assertEquals(0, cursor());
	}

	@Test
	void aChangeOutsideTheSequencesOfThePageIsRefused() throws SQLException {
		postPage(0, 1, List.of(yoghurt(2, "h1", Map.of("el", "Γιαούρτι"), List.of(listing(CATALOGUE_GR, "1.99", true)))), 400);

		assertEquals(0, cursor());
	}

	@Test
	void callersOtherThanTheSyncServiceAccountAreRefused() throws SQLException {
		var page = page(0, 1, List.of(yoghurt(1, "h1", Map.of("el", "Γιαούρτι"), List.of(listing(CATALOGUE_GR, "1.99", true)))));
		for (var token : List.of("retailer", "operator")) {
			as(token).get(BASE + "/cursor").then().statusCode(403);
			as(token).contentType(JSON).body(page).post(BASE).then().statusCode(403);
		}

		assertEquals(0, scalar("SELECT count(*) FROM TH_RETAILER_PRODUCT"));
		assertEquals(0, cursor());
	}

	private static RequestSpecification as(String token) {
		return RestAssured.given().header("Authorization", "Bearer " + token);
	}

	private static void postPage(long fromSequence, long toSequence, List<Map<String, Object>> products, int expectedStatus) {
		as("sync").contentType(JSON).body(page(fromSequence, toSequence, products)).post(BASE).then().statusCode(expectedStatus);
	}

	private static Map<String, Object> page(long fromSequence, long toSequence, List<Map<String, Object>> products) {
		return Map.of("fromSequence", fromSequence, "toSequence", toSequence, "products", products);
	}

	private static Map<String, Object> yoghurt(long changeSequence, String inputHash, Map<String, String> names, List<Map<String, Object>> listings) {
		return product("yoghurt", changeSequence, inputHash, names, Map.of("fat", "2%"), List.of("dairy"), listings);
	}

	private static Map<String, Object> product(String productKey, long changeSequence, String inputHash, Map<String, String> names, Map<String, String> characteristics, List<String> tags, List<Map<String, Object>> listings) {
		var product = new HashMap<String, Object>();
		product.put("retailerId", RETAILER_ID);
		product.put("productKey", productKey);
		product.put("changeSequence", changeSequence);
		product.put("inputHash", inputHash);
		product.put("names", names);
		product.put("characteristics", characteristics);
		product.put("tags", tags);
		product.put("listings", listings);
		product.put("removed", false);
		return product;
	}

	private static Map<String, Object> tombstone(String productKey, long changeSequence) {
		return Map.of("retailerId", RETAILER_ID, "productKey", productKey, "changeSequence", changeSequence, "removed", true);
	}

	private static Map<String, Object> listing(String catalogueId, String price, boolean active) {
		return Map.of("catalogueId", catalogueId, "price", new BigDecimal(price), "currency", "EUR", "active", active);
	}

	private Map<String, String> names(String productKey) throws SQLException {
		return rows("SELECT n.lang, n.name FROM TH_RETAILER_PRODUCT_NAME n JOIN TH_RETAILER_PRODUCT p ON p.id = n.retailer_product_id WHERE p.product_key = '" + productKey + "'");
	}

	private Map<String, String> listings(String productKey) throws SQLException {
		return rows("SELECT l.catalogue_id, l.price || ' ' || l.currency || ' ' || CASE WHEN l.active THEN 'active' ELSE 'inactive' END "
				+ "FROM TH_LISTING l JOIN TH_RETAILER_PRODUCT p ON p.id = l.retailer_product_id WHERE p.product_key = '" + productKey + "'");
	}

	private long cursor() throws SQLException {
		return scalar("SELECT last_sequence FROM TH_CATALOGUE_CHANGE_CURSOR");
	}

	private void resetCursor() throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement()) {
			s.execute("UPDATE TH_CATALOGUE_CHANGE_CURSOR SET last_sequence = 0");
		}
	}

	private Map<String, String> rows(String sql) throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			var result = new TreeMap<String, String>();
			while (rs.next()) {
				result.put(rs.getString(1), rs.getString(2));
			}
			return result;
		}
	}

	private long scalar(String sql) throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}

	private String text(String sql) throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

	private boolean bool(String sql) throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getBoolean(1);
		}
	}

	private static final class StubTokenHelper extends TokenHelperImpl {
		StubTokenHelper() {
			super(null, null, null, null, null);
		}

		@Override
		public Uni<User> processToken(String token) {
			return Uni.createFrom().item(USERS_BY_TOKEN.get(token));
		}

		@Override
		public User makeUnauthenticated() {
			return new UserImpl(null, null, null, false, true, Set.of());
		}
	}
}
