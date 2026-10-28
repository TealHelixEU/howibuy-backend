package eu.tealhelix.catalogue.tests;

import static eu.tealhelix.catalogue.tests.TestFeeds.awaitTheEndOf;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.test.quarkus.WithCatalogueDb;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.path.json.JsonPath;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A Retailer pushes a Feed for one of its Catalogues or for all of them, and reads back the Feed and how its Ingestion
 * Runs stand; who may push to which Catalogue, and who may read which Feed and run. The callers' tokens are stood in
 * for; the e2e test uses real ones.
 */
@QuarkusTest
@WithCatalogueDb
class FeedReceiptTest {
	/** Lime Fresh, the Retailer of the {@code dev} seed, with its active Catalogue {@code lime-fresh-gr}. */
	private static final String LIME_FRESH = "dc73176c-7069-4a4f-a74f-d60c4a5ec1ee";
	/** A Retailer of the tests alone, with the active Catalogues {@code other-lt} and {@code other-lv}, and {@code other-ee} inactive. */
	private static final String OTHER_RETAILER = "0b6a2f4e-35c4-4d8e-a1a7-5e2c9f8d7b22";
	private static final String RETAILER_WITHOUT_CATALOGUES = "9c3e7a1d-4b2f-4e8a-b6d0-3f5a1c8e2d44";
	private static final String OPERATOR = "2e788895-0503-4777-a7bd-24e5d61db5b1";
	private static final String FEED = "{\"productKey\":\"p-1\",\"names\":{\"el\":\"Gala\"},\"price\":\"1.29\",\"currency\":\"EUR\"}\n";
	private static final String FEED_SHA256 = "a7d96021e18e616927be123cad2ea842870a177459e95c7da609bb1f3889049a";
	/** A Feed for both active Catalogues of the Other Retailer, which accept both its languages. */
	private static final String BALTIC_FEED = "{\"productKey\":\"p-1\",\"names\":{\"lt\":\"Pienas\",\"lv\":\"Piens\"},\"price\":\"1.29\",\"currency\":\"EUR\"}\n";

	@Inject
	DataSource dataSource;

	@ConfigProperty(name = "catalogue.feed-store.directory")
	String feedStoreDirectory;

	@BeforeEach
	void createTheCataloguesOfTheTests() throws SQLException {
		execute("INSERT INTO IN_RETAILER (id, name) VALUES ('" + OTHER_RETAILER + "', 'Other Retailer')");
		execute("INSERT INTO IN_CATALOGUE (id, retailer_id, country, primary_language, accepted_languages, adapter, active) VALUES "
				+ "('other-lt', '" + OTHER_RETAILER + "', 'LT', 'lt', '{lt,lv}', 'jsonl', TRUE), "
				+ "('other-lv', '" + OTHER_RETAILER + "', 'LV', 'lv', '{lv,lt}', 'jsonl', TRUE), "
				+ "('other-ee', '" + OTHER_RETAILER + "', 'EE', 'et', '{et}', 'jsonl', FALSE), "
				+ "('lime-fresh-cy', '" + LIME_FRESH + "', 'CY', 'el', '{el}', 'jsonl', FALSE)");
	}

	/**
	 * The runs of the tests' Catalogues are processed by then: the tests that push Feeds wait for their runs.
	 */
	@AfterEach
	void removeTheCataloguesOfTheTests() throws SQLException {
		var catalogues = "('other-lt', 'other-lv', 'other-ee', 'lime-fresh-cy')";
		execute("DELETE FROM IN_LISTING WHERE catalogue_id IN " + catalogues);
		execute("DELETE FROM IN_RETAILER_PRODUCT_NAME WHERE retailer_product_id IN (SELECT id FROM IN_RETAILER_PRODUCT WHERE retailer_id = '" + OTHER_RETAILER + "')");
		execute("DELETE FROM IN_RETAILER_PRODUCT WHERE retailer_id = '" + OTHER_RETAILER + "'");
		execute("DELETE FROM IN_INGESTION_REJECT WHERE ingestion_run_id IN (SELECT id FROM IN_INGESTION_RUN WHERE catalogue_id IN " + catalogues + ")");
		execute("DELETE FROM IN_INGESTION_RUN WHERE catalogue_id IN " + catalogues);
		execute("DELETE FROM IN_FEED WHERE retailer_id = '" + OTHER_RETAILER + "'");
		execute("DELETE FROM IN_CATALOGUE WHERE id IN " + catalogues);
		execute("DELETE FROM IN_RETAILER WHERE id = '" + OTHER_RETAILER + "'");
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aRetailerPushesAFeedForOneCatalogueAndReadsItBack() throws IOException, SQLException, InterruptedException {
		var response = given()
				.contentType("application/x-ndjson")
				.body(FEED.getBytes(UTF_8))
				.when().post("/api/catalogue/v1/feeds/catalogues/lime-fresh-gr?type=SNAPSHOT")
				.then()
				.statusCode(202)
				.extract();
		var receipt = response.jsonPath();
		var feedId = receipt.getString("id");

		assertThat(UUID.fromString(feedId)).isNotNull();
		assertThat(response.header("Location")).endsWith("/api/catalogue/v1/feeds/" + feedId).startsWith("http://");
		assertThat(receipt.getString("feedType")).isEqualTo("SNAPSHOT");
		assertThat(receipt.getString("receivedAt")).isNotNull();
		assertThat(receipt.getLong("fileSize")).isEqualTo(FEED.length());
		assertThat(receipt.getString("fileSha256")).isEqualTo(FEED_SHA256);
		assertThat(receipt.getMap("")).doesNotContainKey("filePath");
		assertThat(receipt.getList("runs.catalogueId")).containsExactly("lime-fresh-gr");
		assertThat(receipt.getList("runs.status")).containsExactly("RECEIVED");
		assertThat(receipt.getList("runs.feedId")).containsExactly(feedId);
		var runId = receipt.getString("runs[0].id");
		assertThat(awaitTheEndOf(dataSource, runId)).isEqualTo("DONE");
		assertThat(gunzip(Path.of(feedStoreDirectory, LIME_FRESH, feedId + ".gz"))).isEqualTo(FEED);

		JsonPath feed = given()
				.when().get(response.header("Location"))
				.then()
				.statusCode(200)
				.extract().jsonPath();

		assertThat(feed.getString("id")).isEqualTo(feedId);
		assertThat(feed.getString("receivedAt")).isEqualTo(receipt.getString("receivedAt"));
		assertThat(feed.getList("runs.id")).containsExactly(runId);
		assertThat(feed.getList("runs.status")).containsExactly("DONE");

		JsonPath run = given()
				.when().get("/api/catalogue/v1/ingestion-runs/" + runId)
				.then()
				.statusCode(200)
				.extract().jsonPath();

		assertThat(run.getString("id")).isEqualTo(runId);
		assertThat(run.getString("feedId")).isEqualTo(feedId);
		assertThat(run.getString("catalogueId")).isEqualTo("lime-fresh-gr");
		assertThat(run.getString("feedType")).isEqualTo("SNAPSHOT");
		assertThat(run.getString("status")).isEqualTo("DONE");
		assertThat(run.getString("adapter")).isEqualTo("jsonl");
		assertThat(run.getString("adapterVersion")).isEqualTo("1");
		assertThat(run.getString("receivedAt")).isEqualTo(receipt.getString("receivedAt"));
		assertThat(run.getString("startedAt")).isNotNull();
		assertThat(run.getString("finishedAt")).isNotNull();
		assertThat(run.getMap("counts")).containsEntry("total", 1).containsEntry("valid", 1).containsEntry("rejected", 0);
		assertThat(run.getMap("")).doesNotContainKeys("filePath", "fileSize", "fileSha256");

		JsonPath rejects = given()
				.when().get("/api/catalogue/v1/ingestion-runs/" + runId + "/rejects?limit=100")
				.then()
				.statusCode(200)
				.extract().jsonPath();

		assertThat(rejects.getList("")).isEmpty();
	}

	@Test
	@TestSecurity(user = "other_retailer", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = OTHER_RETAILER), @Claim(key = "client_id", value = "other_retailer")})
	void aRetailerPushesOneFeedForAllItsActiveCatalogues() throws IOException, SQLException, InterruptedException {
		var response = given()
				.contentType("application/x-ndjson")
				.body(BALTIC_FEED.getBytes(UTF_8))
				.when().post("/api/catalogue/v1/feeds?type=SNAPSHOT")
				.then()
				.statusCode(202)
				.extract();
		var receipt = response.jsonPath();
		var feedId = receipt.getString("id");

		assertThat(response.header("Location")).endsWith("/api/catalogue/v1/feeds/" + feedId);
		assertThat(receipt.getList("runs.catalogueId")).containsExactly("other-lt", "other-lv");
		assertThat(receipt.getList("runs.status")).containsExactly("RECEIVED", "RECEIVED");
		assertThat(receipt.getList("runs.feedId")).containsExactly(feedId, feedId);
		List<String> runIds = receipt.getList("runs.id");
		assertThat(runIds).doesNotHaveDuplicates();
		for (var runId : runIds) {
			assertThat(awaitTheEndOf(dataSource, runId)).isEqualTo("DONE");
		}
		assertThat(gunzip(Path.of(feedStoreDirectory, OTHER_RETAILER, feedId + ".gz"))).isEqualTo(BALTIC_FEED);

		JsonPath feed = given()
				.when().get(response.header("Location"))
				.then()
				.statusCode(200)
				.extract().jsonPath();

		assertThat(feed.getList("runs.id")).isEqualTo(runIds);
		assertThat(feed.getList("runs.status")).containsExactly("DONE", "DONE");
		assertThat(feed.getList("runs.counts.added")).containsExactly(1, 1);
	}

	@Test
	@TestSecurity(user = "retailer_without_catalogues", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = RETAILER_WITHOUT_CATALOGUES), @Claim(key = "client_id", value = "retailer_without_catalogues")})
	void aRetailerWithoutActiveCataloguesPushesNoFeed() {
		given().body(FEED).when().post("/api/catalogue/v1/feeds?type=SNAPSHOT").then().statusCode(403);
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aFeedOfUnknownTypeIsRefused() {
		given().body(FEED).when().post("/api/catalogue/v1/feeds/catalogues/lime-fresh-gr?type=FULL").then().statusCode(400);
		given().body(FEED).when().post("/api/catalogue/v1/feeds/catalogues/lime-fresh-gr").then().statusCode(400);
		given().body(FEED).when().post("/api/catalogue/v1/feeds?type=FULL").then().statusCode(400);
		given().body(FEED).when().post("/api/catalogue/v1/feeds").then().statusCode(400);
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aFeedForAnUnknownCatalogueIsNotFound() {
		given().body(FEED).when().post("/api/catalogue/v1/feeds/catalogues/never-seen?type=SNAPSHOT").then().statusCode(404);
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aRetailerPushesOnlyToItsActiveCatalogues() {
		given().body(FEED).when().post("/api/catalogue/v1/feeds/catalogues/other-lt?type=SNAPSHOT").then().statusCode(403);
		given().body(FEED).when().post("/api/catalogue/v1/feeds/catalogues/lime-fresh-cy?type=SNAPSHOT").then().statusCode(403);
	}

	@Test
	@TestSecurity(user = "operator", roles = "operator")
	@OidcSecurity(claims = @Claim(key = "sub", value = OPERATOR))
	void anOperatorDoesNotPushFeeds() {
		given().body(FEED).when().post("/api/catalogue/v1/feeds/catalogues/lime-fresh-gr?type=SNAPSHOT").then().statusCode(403);
		given().body(FEED).when().post("/api/catalogue/v1/feeds?type=SNAPSHOT").then().statusCode(403);
	}

	@Test
	void anUnauthenticatedCallerIsRefused() {
		given().body(FEED).when().post("/api/catalogue/v1/feeds/catalogues/lime-fresh-gr?type=SNAPSHOT").then().statusCode(401);
		given().body(FEED).when().post("/api/catalogue/v1/feeds?type=SNAPSHOT").then().statusCode(401);
		given().when().get("/api/catalogue/v1/feeds/" + UUID.randomUUID()).then().statusCode(401);
		given().when().get("/api/catalogue/v1/ingestion-runs/" + UUID.randomUUID()).then().statusCode(401);
	}

	@Test
	@TestSecurity(user = "operator", roles = "operator")
	@OidcSecurity(claims = @Claim(key = "sub", value = OPERATOR))
	void anOperatorReadsTheFeedsAndRunsOfAnyRetailer() throws SQLException {
		var received = insertRun("other-lt");

		given().when().get("/api/catalogue/v1/feeds/" + received.feedId()).then().statusCode(200);
		given().when().get("/api/catalogue/v1/ingestion-runs/" + received.runId()).then().statusCode(200);
		given().when().get("/api/catalogue/v1/ingestion-runs/" + received.runId() + "/rejects?limit=1000").then().statusCode(200);
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aRetailerReadsOnlyItsOwnFeedsAndRuns() throws SQLException {
		var received = insertRun("other-lt");

		given().when().get("/api/catalogue/v1/feeds/" + received.feedId()).then().statusCode(403);
		given().when().get("/api/catalogue/v1/ingestion-runs/" + received.runId()).then().statusCode(403);
		given().when().get("/api/catalogue/v1/ingestion-runs/" + received.runId() + "/rejects?limit=10").then().statusCode(403);
	}

	@Test
	@TestSecurity(user = "operator", roles = "operator")
	@OidcSecurity(claims = @Claim(key = "sub", value = OPERATOR))
	void anUnknownFeedOrRunIsNotFound() {
		given().when().get("/api/catalogue/v1/feeds/" + UUID.randomUUID()).then().statusCode(404);
		given().when().get("/api/catalogue/v1/feeds/not-a-uuid").then().statusCode(400);
		given().when().get("/api/catalogue/v1/ingestion-runs/" + UUID.randomUUID()).then().statusCode(404);
		given().when().get("/api/catalogue/v1/ingestion-runs/" + UUID.randomUUID() + "/rejects?limit=10").then().statusCode(404);
		given().when().get("/api/catalogue/v1/ingestion-runs/not-a-uuid").then().statusCode(400);
	}

	@Test
	@TestSecurity(user = "operator", roles = "operator")
	@OidcSecurity(claims = @Claim(key = "sub", value = OPERATOR))
	void thePageOfTheRejectsMustBeBounded() throws SQLException {
		var runId = insertRun("other-lt").runId();

		given().when().get("/api/catalogue/v1/ingestion-runs/" + runId + "/rejects").then().statusCode(400);
		given().when().get("/api/catalogue/v1/ingestion-runs/" + runId + "/rejects?limit=1001").then().statusCode(400);
		given().when().get("/api/catalogue/v1/ingestion-runs/" + runId + "/rejects?limit=10&offset=-1").then().statusCode(400);
	}

	@Test
	void isDescribedInTheOpenApiDocument() {
		JsonPath openApiDocument = given()
				.accept("application/json")
				.when().get("/api/catalogue/v1/openapi")
				.then()
				.statusCode(200)
				.extract().jsonPath();

		assertThat(openApiDocument.getMap("paths")).containsKeys(
				"/api/catalogue/v1/feeds",
				"/api/catalogue/v1/feeds/catalogues/{catalogueId}",
				"/api/catalogue/v1/feeds/{id}",
				"/api/catalogue/v1/ingestion-runs/{id}",
				"/api/catalogue/v1/ingestion-runs/{id}/rejects",
				"/api/catalogue/v1/ingestion-runs/{id}/confirmation",
				"/api/catalogue/v1/ingestion-runs/{id}/discard",
				"/api/catalogue/v1/product-changes"
		);
		assertThat(openApiDocument.getMap("paths")).doesNotContainKey("/api/catalogue/v1/catalogues/{catalogueId}/feeds");
	}

	private record ReceivedRun(String feedId, String runId) {
	}

	/**
	 * A Feed of the Other Retailer, received for one Catalogue and waiting to be processed, which the dispatcher is not
	 * woken for.
	 */
	private ReceivedRun insertRun(String catalogueId) throws SQLException {
		var received = new ReceivedRun(UUID.randomUUID().toString(), UUID.randomUUID().toString());
		execute("INSERT INTO IN_FEED (id, retailer_id, feed_type, received_at, file_path, file_size, file_sha256) VALUES "
				+ "('" + received.feedId() + "', '" + OTHER_RETAILER + "', 'DELTA', '2026-10-20 10:00:00', '" + OTHER_RETAILER + "/" + received.feedId() + ".gz', 10, 'aa')");
		execute("INSERT INTO IN_INGESTION_RUN (id, feed_id, catalogue_id, status) VALUES "
				+ "('" + received.runId() + "', '" + received.feedId() + "', '" + catalogueId + "', 'RECEIVED')");
		return received;
	}

	private void execute(String sql) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static String gunzip(Path file) throws IOException {
		try (var in = new GZIPInputStream(Files.newInputStream(file))) {
			return new String(in.readAllBytes(), UTF_8);
		}
	}
}
