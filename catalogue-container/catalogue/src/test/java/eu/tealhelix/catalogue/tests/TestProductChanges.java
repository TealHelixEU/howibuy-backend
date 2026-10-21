package eu.tealhelix.catalogue.tests;

import static io.restassured.RestAssured.given;

import java.util.Map;

import io.restassured.path.json.JsonPath;
import io.restassured.path.json.config.JsonPathConfig;

/**
 * Reads the Product Changes as the Catalogue Sync does, through the endpoint; the test must be authenticated as the
 * Catalogue Sync. The numbers read back exactly, as {@link java.math.BigDecimal}s.
 */
final class TestProductChanges {
	private static final JsonPathConfig EXACT_NUMBERS = new JsonPathConfig(JsonPathConfig.NumberReturnType.BIG_DECIMAL);

	private TestProductChanges() {
	}

	/**
	 * Read the changes up to the last one.
	 *
	 * @return The value of the change sequence that the next change will follow
	 */
	static long endOfTheChanges() {
		long after = 0;
		JsonPath page;
		while (!(page = changesAfter(after, 1000)).getList("products").isEmpty()) {
			after = page.getLong("toSequence");
		}
		return after;
	}

	/**
	 * The change of a product after a value of the change sequence, among those of the products that other tests left
	 * behind.
	 *
	 * @return The change, {@code null} if the product did not change
	 */
	static Map<String, Object> changeOf(long after, String productKey) {
		return changesAfter(after, 1000).getMap("products.find { it.productKey == '" + productKey + "' }");
	}

	static JsonPath changesAfter(long after, int limit) {
		return given().when().get("/api/catalogue/v1/product-changes?after=" + after + "&limit=" + limit)
				.then().statusCode(200)
				.extract().jsonPath().using(EXACT_NUMBERS);
	}
}
