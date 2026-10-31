package eu.tealhelix.howibuy.tests;

import static io.restassured.http.ContentType.JSON;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import javax.sql.DataSource;

import eu.tealhelix.common.v1.model.Role;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.jaxrs.authentication.jwt.Authentication;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

/**
 * The Retailer, its two Catalogues and the products that the assessment tests assess, against a real database. The
 * products are brought in through the Catalogue Changes as the Catalogue Sync brings them, and classified through
 * Classification Attempts, with the tokens {@code sync} and {@code operator} of {@link #authenticationsWith(Map)}.
 */
final class AssessmentFixtures {
	static final String BASE = "/api/howibuy/v1";

	static final String RETAILER_ID = "6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11";
	static final String CATALOGUE_GR = "test-gr";
	static final String CATALOGUE_CY = "test-cy";

	private static final User CATALOGUE_SYNC = new UserImpl(new UserIdImpl("1d0c6b0e-3b5f-4d2a-8f7e-2a9c4b6d8e33"), "catalogue-sync", null, false, false, Set.of(Role.CATALOGUE_SYNC));
	private static final User OPERATOR = new UserImpl(new UserIdImpl("4e2d8c1a-7b3f-4a6e-9c5d-3f1b7a9e2c44"), "operator", null, false, true, Set.of(Role.OPERATOR));

	private final DataSource dataSource;

	AssessmentFixtures(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @return The authentications of the tokens of a test, along with those that bring the products in and classify them
	 */
	static Map<String, Authentication> authenticationsWith(Map<String, Authentication> authenticationsByToken) {
		var all = new HashMap<>(authenticationsByToken);
		all.put("sync", new Authentication(CATALOGUE_SYNC, null));
		all.put("operator", new Authentication(OPERATOR, null));
		return Map.copyOf(all);
	}

	/**
	 * No products, and the Retailer and its two Catalogues in place.
	 */
	void reset() throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement()) {
			s.execute("DELETE FROM TH_LISTING");
			s.execute("DELETE FROM TH_RETAILER_PRODUCT_NAME");
			s.execute("DELETE FROM TH_RETAILER_PRODUCT");
			s.execute("INSERT INTO TH_RETAILER (id, name) VALUES ('" + RETAILER_ID + "', 'Test Retailer') ON CONFLICT (id) DO NOTHING");
			s.execute("UPDATE TH_RETAILER SET classification_language = NULL WHERE id = '" + RETAILER_ID + "'");
			s.execute("INSERT INTO TH_CATALOGUE (id, retailer_id, country, primary_language) VALUES ('" + CATALOGUE_GR + "', '" + RETAILER_ID + "', 'GR', 'el') ON CONFLICT (id) DO NOTHING");
			s.execute("INSERT INTO TH_CATALOGUE (id, retailer_id, country, primary_language) VALUES ('" + CATALOGUE_CY + "', '" + RETAILER_ID + "', 'CY', 'el') ON CONFLICT (id) DO NOTHING");
			s.execute("UPDATE TH_CATALOGUE_CHANGE_CURSOR SET last_sequence = 0");
		}
	}

	static RequestSpecification as(String token) {
		return RestAssured.given().header("Authorization", "Bearer " + token);
	}

	/**
	 * An expert's attempt resolves the Classification of the product.
	 */
	void classify(String productKey, String inputHash, String archetypeProductId) {
		var attempt = new HashMap<String, Object>();
		attempt.put("retailerProductId", productId(productKey));
		attempt.put("inputHash", inputHash);
		attempt.put("language", "el");
		attempt.put("classifier", "EXPERT");
		attempt.put("classifierVersion", "test");
		attempt.put("archetypeProductId", archetypeProductId);
		as("operator").contentType(JSON).body(attempt).post(BASE + "/classification-attempts").then().statusCode(204);
	}

	static Map<String, Object> listing(String catalogueId, boolean active) {
		return listing(catalogueId, active, "1.99");
	}

	static Map<String, Object> listing(String catalogueId, boolean active, String price) {
		return Map.of("catalogueId", catalogueId, "price", new BigDecimal(price), "currency", "EUR", "active", active);
	}

	/**
	 * Apply the product, named in Greek by its key, as the next change of the Catalogue Sync.
	 */
	void applyProduct(String productKey, long changeSequence, String inputHash, Map<String, Object> listing) {
		applyProduct(productKey, changeSequence, inputHash, Map.of("el", productKey), listing);
	}

	/**
	 * Apply the product as the next change of the Catalogue Sync.
	 */
	void applyProduct(String productKey, long changeSequence, String inputHash, Map<String, String> names, Map<String, Object> listing) {
		var product = new HashMap<String, Object>();
		product.put("retailerId", RETAILER_ID);
		product.put("productKey", productKey);
		product.put("changeSequence", changeSequence);
		product.put("inputHash", inputHash);
		product.put("names", names);
		product.put("characteristics", Map.of());
		product.put("tags", List.of());
		product.put("listings", List.of(listing));
		product.put("removed", false);
		as("sync").contentType(JSON)
				.body(Map.of("fromSequence", changeSequence - 1, "toSequence", changeSequence, "products", List.of(product)))
				.post(BASE + "/catalogue-changes")
				.then().statusCode(204);
	}

	String productId(String productKey) {
		try {
			return rows("SELECT id::text FROM TH_RETAILER_PRODUCT WHERE product_key = '" + productKey + "'").getFirst();
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * @return Each row, its columns joined by {@code |}
	 */
	List<String> rows(String sql) throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			var rows = new ArrayList<String>();
			var columns = rs.getMetaData().getColumnCount();
			while (rs.next()) {
				var row = new StringJoiner("|");
				for (int i = 1; i <= columns; i++) {
					row.add(rs.getString(i) != null ? rs.getString(i) : "null");
				}
				rows.add(row.toString());
			}
			return rows;
		}
	}
}
