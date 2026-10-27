package eu.tealhelix.cataloguesync.e2e;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import eu.tealhelix.common.test.quarkus.ServiceAccount;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A user of a Retailer scans a product in its Catalogue and is recommended products the Catalogue stocks. The Retailer
 * pushes a Snapshot of its Catalogue, the Catalogue Sync carries it into HowiBuy, the products are classified by
 * attempts recorded from outside, and the Retailer exchanges its token for one that acts as its user in the Catalogue.
 * It runs the pipeline of {@link CatalogueSyncIT}.
 */
class ProductAssessmentIT {
	/** The archetype "Yogurt, fermented milk or dairy specialty, plain, w cream" of the seed data. */
	private static final String YOGHURT_WITH_CREAM = "afc302d8-4586-5720-96fa-22d9f2b4ea15";
	/** The archetype "Yogurt, fermented milk or dairy specialty, plain" of the seed data. */
	private static final String PLAIN_YOGHURT = "66b23d9a-bed9-5d45-a6c7-c5b51c097503";

	private static final Pipeline pipeline = new Pipeline("product-assessment");

	@BeforeAll
	static void startThePipeline() throws IOException, InterruptedException {
		pipeline.start(Map.of());
	}

	@AfterAll
	static void stopThePipeline() throws InterruptedException {
		pipeline.close();
	}

	/**
	 * The price is written as a plain number without its trailing zeros: 1.8, rather than 1.80.
	 */
	@Test
	void aUserIsRecommendedTheCheapestProductOfTheCatalogueThatStandsForABetterArchetype() throws Exception {
		assertThat(pipeline.pushSnapshot("lime-fresh-gr-yoghurts.jsonl")).isEqualTo("DONE");
		pipeline.runCatalogueSync("sync", Map.of(), "sync");
		classify(Map.of(
				"e2e-yoghurt-with-cream", YOGHURT_WITH_CREAM,
				"e2e-yoghurt", PLAIN_YOGHURT,
				"e2e-yoghurt-organic", PLAIN_YOGHURT
		));

		var body = given()
				.header("Authorization", "Bearer " + userAccessToken("e2e-shopper"))
				.contentType(JSON)
				.body(Map.of("productKey", "e2e-yoghurt-with-cream", "language", "en"))
				.when().post(pipeline.howiBuyUrl() + "/api/howibuy/v1/assessment/single")
				.then()
				.statusCode(200)
				.body("type", equalTo("SUCCESS"))
				.body("diagnostics.archetypeProductId", equalTo(YOGHURT_WITH_CREAM))
				.body("bestScientificAlternative.type", equalTo("SUGGESTION"))
				.body("bestScientificAlternative.archetype.id", equalTo(PLAIN_YOGHURT))
				.body("bestScientificAlternative.product.productKey", equalTo("e2e-yoghurt"))
				.body("bestScientificAlternative.product.name", equalTo("Plain yoghurt"))
				.body("bestScientificAlternative.product.language", equalTo("en"))
				.body("bestScientificAlternative.product.currency", equalTo("EUR"))
				.extract().asString();

		assertThat(body).containsPattern(Pattern.compile("\"price\":1\\.8[,}]"));
	}

	/**
	 * Record a manual attempt at each product as the Catalogue Sync, with the input hash HowiBuy reads it pending with.
	 *
	 * @param archetypesByProductKey The archetype each product is classified to
	 */
	private static void classify(Map<String, String> archetypesByProductKey) throws SQLException {
		var accessToken = pipeline.accessToken(ServiceAccount.CATALOGUE_SYNC);
		var productKeysById = productKeysById();
		var pending = given()
				.header("Authorization", "Bearer " + accessToken)
				.queryParam("limit", 100)
				.when().get(pipeline.howiBuyUrl() + "/api/howibuy/v1/classifications/pending")
				.then().statusCode(200)
				.extract().jsonPath().getList("", Map.class);
		assertThat(pending).hasSameSizeAs(archetypesByProductKey.keySet());
		for (var product : pending) {
			var attempt = new HashMap<String, Object>();
			attempt.put("retailerProductId", product.get("retailerProductId"));
			attempt.put("inputHash", product.get("inputHash"));
			attempt.put("language", "el");
			attempt.put("classifier", "MANUAL");
			attempt.put("classifierVersion", "e2e");
			attempt.put("archetypeProductId", archetypesByProductKey.get(productKeysById.get(product.get("retailerProductId"))));
			given()
					.header("Authorization", "Bearer " + accessToken)
					.contentType(JSON)
					.body(attempt)
					.when().post(pipeline.howiBuyUrl() + "/api/howibuy/v1/classification-attempts")
					.then().statusCode(204);
		}
	}

	/**
	 * Exchange the Retailer's token for one that acts as its user in the Catalogue.
	 *
	 * @param correlationId The Retailer's own id for the user
	 */
	private static String userAccessToken(String correlationId) {
		return given()
				.header("Authorization", "Bearer " + pipeline.accessToken(ServiceAccount.RETAILER))
				.contentType(JSON)
				.body(Map.of("correlationId", correlationId, "catalogueId", Pipeline.CATALOGUE_ID))
				.when().post(pipeline.howiBuyUrl() + "/api/howibuy/v1/tokenexchange")
				.then().statusCode(200)
				.extract().path("access_token");
	}

	/**
	 * @return The keys of the Retailer Products in HowiBuy, by their ids
	 */
	private static Map<String, String> productKeysById() throws SQLException {
		try (var connection = pipeline.howiBuyConnection(); var statement = connection.createStatement();
				var resultSet = statement.executeQuery("SELECT id::text, product_key FROM TH_RETAILER_PRODUCT")) {
			var productKeysById = new HashMap<String, String>();
			while (resultSet.next()) {
				productKeysById.put(resultSet.getString(1), resultSet.getString(2));
			}
			return productKeysById;
		}
	}
}
