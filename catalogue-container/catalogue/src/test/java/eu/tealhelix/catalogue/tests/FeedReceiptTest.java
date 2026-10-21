package eu.tealhelix.catalogue.tests;

import static eu.tealhelix.catalogue.tests.TestFeeds.awaitTheEndOf;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
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
 * A Retailer pushes a Feed for one of its Catalogues and reads back how its Ingestion Run stands; who may push to which
 * Catalogue, and who may read which run. The callers' tokens are stood in for; the e2e test uses real ones.
 */
@QuarkusTest
@WithCatalogueDb
class FeedReceiptTest {
	/** Lime Fresh, the Retailer of the {@code dev} seed, with its active Catalogue {@code lime-fresh-gr}. */
	private static final String LIME_FRESH = "dc73176c-7069-4a4f-a74f-d60c4a5ec1ee";
	private static final String OTHER_RETAILER = "0b6a2f4e-35c4-4d8e-a1a7-5e2c9f8d7b22";
	private static final String OPERATOR = "2e788895-0503-4777-a7bd-24e5d61db5b1";
	private static final String FEED = "{\"productKey\":\"p-1\",\"names\":{\"el\":\"Gala\"},\"price\":\"1.29\",\"currency\":\"EUR\"}\n";
	private static final String FEED_SHA256 = "a7d96021e18e616927be123cad2ea842870a177459e95c7da609bb1f3889049a";

	@Inject
	DataSource dataSource;

	@ConfigProperty(name = "catalogue.feed-store.directory")
	String feedStoreDirectory;

	@BeforeEach
	void createTheCataloguesOfTheTests() throws SQLException {
		execute("INSERT INTO IN_RETAILER (id, name) VALUES ('" + OTHER_RETAILER + "', 'Other Retailer')");
		execute("INSERT INTO IN_CATALOGUE (id, retailer_id, country, primary_language, accepted_languages, adapter, active) VALUES "
				+ "('other-lt', '" + OTHER_RETAILER + "', 'LT', 'lt', '{lt}', 'test-jsonl', TRUE), "
				+ "('lime-fresh-cy', '" + LIME_FRESH + "', 'CY', 'el', '{el}', 'test-jsonl', FALSE)");
	}

	@AfterEach
	void removeTheCataloguesOfTheTests() throws SQLException {
		execute("DELETE FROM IN_INGESTION_RUN WHERE catalogue_id IN ('other-lt', 'lime-fresh-cy')");
		execute("DELETE FROM IN_CATALOGUE WHERE id IN ('other-lt', 'lime-fresh-cy')");
		execute("DELETE FROM IN_RETAILER WHERE id = '" + OTHER_RETAILER + "'");
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aRetailerPushesAFeedAndReadsItsRunBack() throws IOException, SQLException, InterruptedException {
		var response = given()
				.contentType("application/x-ndjson")
				.body(FEED.getBytes(UTF_8))
				.when().post("/api/catalogue/v1/catalogues/lime-fresh-gr/feeds?type=SNAPSHOT")
				.then()
				.statusCode(202)
				.extract();
		var receipt = response.jsonPath();
		var runId = receipt.getString("id");

		assertThat(UUID.fromString(runId)).isNotNull();
		assertThat(receipt.getString("status")).isEqualTo("RECEIVED");
		assertThat(receipt.getString("statusUrl")).isEqualTo(response.header("Location")).endsWith("/api/catalogue/v1/ingestion-runs/" + runId).startsWith("http://");
		assertThat(awaitTheEndOf(dataSource, runId)).isEqualTo("DONE");

		JsonPath run = given()
				.when().get(receipt.getString("statusUrl"))
				.then()
				.statusCode(200)
				.extract().jsonPath();

		assertThat(run.getString("id")).isEqualTo(runId);
		assertThat(run.getString("catalogueId")).isEqualTo("lime-fresh-gr");
		assertThat(run.getString("feedType")).isEqualTo("SNAPSHOT");
		assertThat(run.getString("status")).isEqualTo("DONE");
		assertThat(run.getString("adapter")).isEqualTo("test-jsonl");
		assertThat(run.getString("adapterVersion")).isEqualTo("1");
		assertThat(run.getString("receivedAt")).isNotNull();
		assertThat(run.getString("startedAt")).isNotNull();
		assertThat(run.getString("finishedAt")).isNotNull();
		assertThat(run.getLong("fileSize")).isEqualTo(FEED.length());
		assertThat(run.getString("fileSha256")).isEqualTo(FEED_SHA256);
		assertThat(run.getMap("counts")).containsEntry("total", 1).containsEntry("valid", 1).containsEntry("rejected", 0);
		assertThat(run.getMap("")).doesNotContainKey("filePath");
		assertThat(gunzip(Path.of(feedStoreDirectory, "lime-fresh-gr", runId + ".gz"))).isEqualTo(FEED);

		JsonPath rejects = given()
				.when().get(receipt.getString("statusUrl") + "/rejects?limit=100")
				.then()
				.statusCode(200)
				.extract().jsonPath();

		assertThat(rejects.getList("")).isEmpty();
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aFeedOfUnknownTypeIsRefused() {
		given().body(FEED).when().post("/api/catalogue/v1/catalogues/lime-fresh-gr/feeds?type=FULL").then().statusCode(400);
		given().body(FEED).when().post("/api/catalogue/v1/catalogues/lime-fresh-gr/feeds").then().statusCode(400);
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aFeedForAnUnknownCatalogueIsNotFound() {
		given().body(FEED).when().post("/api/catalogue/v1/catalogues/never-seen/feeds?type=SNAPSHOT").then().statusCode(404);
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aRetailerPushesOnlyToItsActiveCatalogues() {
		given().body(FEED).when().post("/api/catalogue/v1/catalogues/other-lt/feeds?type=SNAPSHOT").then().statusCode(403);
		given().body(FEED).when().post("/api/catalogue/v1/catalogues/lime-fresh-cy/feeds?type=SNAPSHOT").then().statusCode(403);
	}

	@Test
	@TestSecurity(user = "operator", roles = "operator")
	@OidcSecurity(claims = @Claim(key = "sub", value = OPERATOR))
	void anOperatorDoesNotPushFeeds() {
		given().body(FEED).when().post("/api/catalogue/v1/catalogues/lime-fresh-gr/feeds?type=SNAPSHOT").then().statusCode(403);
	}

	@Test
	void anUnauthenticatedCallerIsRefused() {
		given().body(FEED).when().post("/api/catalogue/v1/catalogues/lime-fresh-gr/feeds?type=SNAPSHOT").then().statusCode(401);
		given().when().get("/api/catalogue/v1/ingestion-runs/" + UUID.randomUUID()).then().statusCode(401);
	}

	@Test
	@TestSecurity(user = "operator", roles = "operator")
	@OidcSecurity(claims = @Claim(key = "sub", value = OPERATOR))
	void anOperatorReadsTheRunOfAnyRetailer() throws SQLException {
		var runId = insertRun("other-lt");

		given().when().get("/api/catalogue/v1/ingestion-runs/" + runId).then().statusCode(200);
		given().when().get("/api/catalogue/v1/ingestion-runs/" + runId + "/rejects?limit=1000").then().statusCode(200);
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aRetailerReadsOnlyTheRunsOfItsCatalogues() throws SQLException {
		var runId = insertRun("other-lt");

		given().when().get("/api/catalogue/v1/ingestion-runs/" + runId).then().statusCode(403);
		given().when().get("/api/catalogue/v1/ingestion-runs/" + runId + "/rejects?limit=10").then().statusCode(403);
	}

	@Test
	@TestSecurity(user = "operator", roles = "operator")
	@OidcSecurity(claims = @Claim(key = "sub", value = OPERATOR))
	void anUnknownRunIsNotFound() {
		given().when().get("/api/catalogue/v1/ingestion-runs/" + UUID.randomUUID()).then().statusCode(404);
		given().when().get("/api/catalogue/v1/ingestion-runs/" + UUID.randomUUID() + "/rejects?limit=10").then().statusCode(404);
		given().when().get("/api/catalogue/v1/ingestion-runs/not-a-uuid").then().statusCode(400);
	}

	@Test
	@TestSecurity(user = "operator", roles = "operator")
	@OidcSecurity(claims = @Claim(key = "sub", value = OPERATOR))
	void thePageOfTheRejectsMustBeBounded() throws SQLException {
		var runId = insertRun("other-lt");

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
				"/api/catalogue/v1/catalogues/{catalogueId}/feeds",
				"/api/catalogue/v1/ingestion-runs/{id}",
				"/api/catalogue/v1/ingestion-runs/{id}/rejects",
				"/api/catalogue/v1/ingestion-runs/{id}/confirmation",
				"/api/catalogue/v1/ingestion-runs/{id}/discard"
		);
	}

	private String insertRun(String catalogueId) throws SQLException {
		var runId = UUID.randomUUID().toString();
		execute("INSERT INTO IN_INGESTION_RUN (id, catalogue_id, feed_type, status, received_at, file_path, file_size, file_sha256) VALUES "
				+ "('" + runId + "', '" + catalogueId + "', 'DELTA', 'RECEIVED', '2026-10-20 10:00:00', '" + catalogueId + "/" + runId + ".gz', 10, 'aa')");
		return runId;
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
