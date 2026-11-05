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
 * A user of a Retailer scans a product in its Catalogue and is offered an Alternative the Catalogue stocks. The Retailer
 * pushes a Snapshot of its Catalogue, the Catalogue Sync carries it into HowiBuy, the products are classified by
 * attempts recorded from outside, and the Retailer exchanges its token for one that acts as its user in the Catalogue.
 * It runs the pipeline of {@link CatalogueSyncIT}.
 */
class ProductAssessmentIT {
	/** The archetype "Butter, 82% fat, unsalted" of the seed data, in the category Animal fat. */
	private static final String BUTTER = "b71fbfa9-bad1-52fc-9de9-d18c257de6d0";
	/** The archetype "Olive oil, extra virgin" of the seed data, in Vegetable oil, which substitutes for Animal fat at degree 4. */
	private static final String OLIVE_OIL = "261e41a6-faaa-5251-bc32-c9fa0f966c95";
	/** The scientific Overall Score at or above which a product is good enough. */
	private static final double GATE = 0.80;

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
	 * The butter does not pass the Gate, and the olive oil is all its Pool holds in a Catalogue that stocks nothing else,
	 * so it is offered whatever the draw. The price is written as a plain number without its trailing zeros: 6.8, rather
	 * than 6.80.
	 */
	@Test
	void aUserIsOfferedTheCheapestProductOfTheCatalogueThatStandsForTheAlternative() throws Exception {
		assertThat(pipeline.pushSnapshot("lime-fresh-gr-butter-and-olive-oil.jsonl")).isEqualTo("DONE");
		pipeline.runCatalogueSync("sync", Map.of(), "sync");
		classify(Map.of(
				"e2e-butter", BUTTER,
				"e2e-olive-oil", OLIVE_OIL,
				"e2e-olive-oil-organic", OLIVE_OIL
		));

		var response = given()
				.header("Authorization", "Bearer " + userAccessToken("e2e-shopper"))
				.contentType(JSON)
				.body(Map.of("productKey", "e2e-butter", "language", "en"))
				.when().post(pipeline.howiBuyUrl() + "/api/howibuy/v1/assessment/product/single")
				.then()
				.statusCode(200)
				.body("type", equalTo("SUCCESS"))
				.body("diagnostics.archetypeProductId", equalTo(BUTTER))
				.body("recommendation", equalTo("RECOMMENDED"))
				.body("alternatives.size()", equalTo(1))
				.body("alternatives[0].archetype.id", equalTo(OLIVE_OIL))
				.body("alternatives[0].substitutability", equalTo(4))
				.body("alternatives[0].product.productKey", equalTo("e2e-olive-oil"))
				.body("alternatives[0].product.name", equalTo("Extra virgin olive oil"))
				.body("alternatives[0].product.language", equalTo("en"))
				.body("alternatives[0].product.currency", equalTo("EUR"))
				.extract();

		assertThat(response.asString()).containsPattern(Pattern.compile("\"price\":6\\.8[,}]"));
		double butterScore = response.jsonPath().getDouble("referenceOverallScore");
		assertThat(butterScore).as("the butter's scientific Overall Score").isLessThan(GATE);
		assertThat(response.jsonPath().getDouble("alternatives[0].overallScore")).as("the olive oil's").isGreaterThan(butterScore);
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
