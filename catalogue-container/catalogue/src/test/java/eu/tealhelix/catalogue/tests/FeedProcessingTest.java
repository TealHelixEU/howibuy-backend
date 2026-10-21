package eu.tealhelix.catalogue.tests;

import static eu.tealhelix.catalogue.tests.TestFeeds.awaitTheEndOf;
import static eu.tealhelix.catalogue.tests.TestFeeds.feed;
import static eu.tealhelix.catalogue.tests.TestFeeds.product;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.services.v1.impl.FeedStore;
import eu.tealhelix.catalogue.services.v1.impl.IngestionRunDispatcher;
import eu.tealhelix.catalogue.test.quarkus.WithCatalogueDb;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What processing a Feed does to the Retailer Products and Listings of its Catalogue, in the database: new, changed and
 * unchanged products, the change sequence, the classification input hash, the names merged across languages, the rejects,
 * the order of the runs and the recovery of an interrupted run. Lime Fresh pushes the Feeds to its Catalogue of the
 * {@code dev} seed, {@code lime-fresh-gr} (primary language {@code el}, also {@code en}), in the format of the tests'
 * adapter. Each test names its products apart, as the tests share the database.
 */
@QuarkusTest
@WithCatalogueDb
@TestSecurity(user = "lime_fresh", roles = "retailer")
@OidcSecurity(claims = {@Claim(key = "sub", value = FeedProcessingTest.LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
class FeedProcessingTest {
	static final String LIME_FRESH = "dc73176c-7069-4a4f-a74f-d60c4a5ec1ee";
	private static final String CATALOGUE = "lime-fresh-gr";

	@Inject
	DataSource dataSource;

	@Inject
	FeedStore feedStore;

	@Inject
	IngestionRunDispatcher dispatcher;

	private String prefix;

	@BeforeEach
	void nameTheProductsApart() {
		prefix = UUID.randomUUID().toString().substring(0, 8) + "-";
	}

	@Test
	void tellsNewChangedAndUnchangedProductsApartAcrossTwoRuns() throws Exception {
		var first = process("SNAPSHOT", feed(
				product(key("same"), Map.of("el", "Γάλα"), "1.29"),
				product(key("renamed"), Map.of("el", "Τυρί"), "4.50"),
				product(key("repriced"), Map.of("el", "Ψωμί"), "0.99")
		));
		assertThat(counts(first)).isEqualTo(Map.of("total", 3, "valid", 3, "rejected", 0, "added", 3, "changed", 0, "unchanged", 0, "deactivated", 0));
		var before = changeSequences();

		var second = process("SNAPSHOT", feed(
				product(key("same"), Map.of("el", "Γάλα"), "1.29"),
				product(key("renamed"), Map.of("el", "Τυρί φέτα"), "4.50"),
				product(key("repriced"), Map.of("el", "Ψωμί"), "1.09"),
				product(key("new"), Map.of("el", "Αυγά"), "2.10")
		));

		assertThat(counts(second)).isEqualTo(Map.of("total", 4, "valid", 4, "rejected", 0, "added", 1, "changed", 2, "unchanged", 1, "deactivated", 0));
		var after = changeSequences();
		assertThat(after.get(key("same"))).as("an unchanged product keeps its stamp").isEqualTo(before.get(key("same")));
		var latestBefore = before.values().stream().mapToLong(Long::longValue).max().orElseThrow();
		assertThat(after.get(key("renamed"))).as("a changed product is stamped anew").isGreaterThan(latestBefore);
		assertThat(after.get(key("repriced"))).as("a changed price stamps its product anew").isGreaterThan(latestBefore);
		assertThat(after.get(key("new"))).as("a new product is stamped").isGreaterThan(latestBefore);
		assertThat(listing(key("repriced"))).isEqualTo(new Listing(new BigDecimal("1.0900"), "EUR", true, second));
		assertThat(listing(key("same"))).as("an unchanged product is seen by the latest run").isEqualTo(new Listing(new BigDecimal("1.2900"), "EUR", true, second));
	}

	@Test
	void aPriceOnlyChangeStampsTheProductButKeepsTheHash() throws Exception {
		process("DELTA", feed(product(key("milk"), Map.of("el", "Γάλα"), Map.of("fat", "3.5%"), List.of("dairy"), "1.29")));
		var hash = inputHash(key("milk"));
		var stamp = changeSequences().get(key("milk"));

		process("DELTA", feed(product(key("milk"), Map.of("el", "Γάλα"), Map.of("fat", "3.5%"), List.of("dairy"), "1.19")));

		assertThat(inputHash(key("milk"))).isEqualTo(hash);
		assertThat(changeSequences().get(key("milk"))).isGreaterThan(stamp);
	}

	@Test
	void theNamesOfARecordReplaceThoseOfItsLanguagesOnly() throws Exception {
		process("DELTA", feed(product(key("milk"), Map.of("el", "Γάλα", "en", "Milk"), "1.29")));
		var hash = inputHash(key("milk"));

		process("DELTA", feed(product(key("milk"), Map.of("el", "Γάλα αγελάδας"), "1.29")));

		assertThat(names(key("milk"))).isEqualTo(Map.of("el", "Γάλα αγελάδας", "en", "Milk"));
		assertThat(inputHash(key("milk"))).isNotEqualTo(hash);
	}

	@Test
	void countsAndListsTheRejects() throws Exception {
		var runId = process("SNAPSHOT", feed(
				product(key("valid"), Map.of("el", "Γάλα"), "1.29"),
				"not json",
				product(key("no-price"), Map.of("el", "Τυρί"), null),
				product(key("german"), Map.of("el", "Ψωμί", "de", "Brot"), "0.99"),
				product(key("free"), Map.of("el", "Αυγά"), "0")
		));

		assertThat(counts(runId)).isEqualTo(Map.of("total", 5, "valid", 1, "rejected", 4, "added", 1, "changed", 0, "unchanged", 0, "deactivated", 0));
		assertThat(rejects(runId)).containsExactly(
				Map.of("position", 2, "reason", "The record is not a JSON object"),
				Map.of("position", 3, "productKey", key("no-price"), "reason", "The price is missing"),
				Map.of("position", 4, "productKey", key("german"), "reason", "The Catalogue does not accept the language, language: de"),
				Map.of("position", 5, "productKey", key("free"), "reason", "The price is not positive, price: 0")
		);
		assertThat(changeSequences()).containsOnlyKeys(key("valid"));
	}

	@Test
	void theRunsOfACatalogueAreProcessedOneAfterTheOtherInOrderOfReceipt() throws Exception {
		var first = send("DELTA", feed(product(key("milk"), Map.of("el", "Γάλα"), "1.29"), product(key("bread"), Map.of("el", "Ψωμί"), "0.99")));
		var second = send("DELTA", feed(product(key("milk"), Map.of("el", "Γάλα"), "1.19")));

		assertThat(awaitTheEndOf(dataSource, first)).isEqualTo("DONE");
		assertThat(awaitTheEndOf(dataSource, second)).isEqualTo("DONE");

		assertThat(listing(key("milk")).price()).isEqualByComparingTo("1.19");
		assertThat(timestamp(second, "started_at")).isAfterOrEqualTo(timestamp(first, "finished_at"));
	}

	@Test
	void aDeletionFeedFailsItsRun() throws Exception {
		var runId = send("DELETION", feed(key("milk")));

		assertThat(awaitTheEndOf(dataSource, runId)).isEqualTo("FAILED");
		assertThat(run(runId).getString("error")).isEqualTo("Deletion Feeds are not processed yet");
	}

	@Test
	void aRunInterruptedByAStopIsProcessedAgainWhenTheIngestionStarts() throws Exception {
		var runId = UUID.randomUUID().toString();
		var storedFeed = feedStore.store(new CatalogueIdImpl(CATALOGUE), new IngestionRunIdImpl(runId), new ByteArrayInputStream(feed(
				product(key("milk"), Map.of("el", "Γάλα"), "1.29"),
				"not json"
		).getBytes(UTF_8)));
		execute("INSERT INTO IN_INGESTION_RUN (id, catalogue_id, feed_type, status, adapter, adapter_version, received_at, started_at, file_path, file_size, file_sha256) VALUES "
				+ "('" + runId + "', '" + CATALOGUE + "', 'DELTA', 'PROCESSING', 'test-jsonl', '1', now(), now(), '" + storedFeed.path() + "', " + storedFeed.size() + ", '" + storedFeed.sha256() + "')");
		execute("INSERT INTO IN_INGESTION_REJECT (ingestion_run_id, position, product_key, reason) VALUES ('" + runId + "', 2, NULL, 'The record is not a JSON object')");

		dispatcher.recover();

		assertThat(awaitTheEndOf(dataSource, runId)).isEqualTo("DONE");
		assertThat(counts(runId)).isEqualTo(Map.of("total", 2, "valid", 1, "rejected", 1, "added", 1, "changed", 0, "unchanged", 0, "deactivated", 0));
		assertThat(rejects(runId)).containsExactly(Map.of("position", 2, "reason", "The record is not a JSON object"));
		assertThat(changeSequences()).containsOnlyKeys(key("milk"));
	}

	private String key(String name) {
		return prefix + name;
	}

	/**
	 * Send a Feed and wait until its run is done.
	 *
	 * @return The id of the run
	 */
	private String process(String feedType, String feed) throws SQLException, InterruptedException {
		var runId = send(feedType, feed);
		assertThat(awaitTheEndOf(dataSource, runId)).isEqualTo("DONE");
		return runId;
	}

	private String send(String feedType, String feed) {
		return given()
				.contentType("application/x-ndjson")
				.body(feed.getBytes(UTF_8))
				.when().post("/api/catalogue/v1/catalogues/" + CATALOGUE + "/feeds?type=" + feedType)
				.then()
				.statusCode(202)
				.extract().jsonPath().getString("id");
	}

	private JsonPath run(String runId) {
		return given().when().get("/api/catalogue/v1/ingestion-runs/" + runId).then().statusCode(200).extract().jsonPath();
	}

	private Map<String, Integer> counts(String runId) {
		return run(runId).getMap("counts", String.class, Integer.class);
	}

	private List<Map<String, Object>> rejects(String runId) {
		List<Map<String, Object>> rejects = given().when().get("/api/catalogue/v1/ingestion-runs/" + runId + "/rejects?limit=100")
				.then().statusCode(200)
				.extract().jsonPath().getList("$");
		rejects.forEach(reject -> reject.values().removeIf(value -> value == null));
		return rejects;
	}

	/**
	 * The change sequences of this test's products, by product key.
	 */
	private Map<String, Long> changeSequences() throws SQLException {
		var changeSequences = new HashMap<String, Long>();
		try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT product_key, change_sequence FROM IN_RETAILER_PRODUCT WHERE product_key LIKE ?")) {
			statement.setString(1, prefix + "%");
			try (var resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					changeSequences.put(resultSet.getString(1), resultSet.getLong(2));
				}
			}
		}
		return changeSequences;
	}

	private String inputHash(String productKey) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT input_hash FROM IN_RETAILER_PRODUCT WHERE product_key = ?")) {
			statement.setString(1, productKey);
			try (var resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getString(1);
			}
		}
	}

	private Map<String, String> names(String productKey) throws SQLException {
		var names = new HashMap<String, String>();
		try (
				var connection = dataSource.getConnection();
				var statement = connection.prepareStatement("""
						SELECT n.lang, n.name
						FROM IN_RETAILER_PRODUCT_NAME n JOIN IN_RETAILER_PRODUCT p ON p.id = n.retailer_product_id
						WHERE p.product_key = ?
						""")
		) {
			statement.setString(1, productKey);
			try (var resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					names.put(resultSet.getString(1), resultSet.getString(2));
				}
			}
		}
		return names;
	}

	private record Listing(BigDecimal price, String currency, boolean active, String lastSeenRunId) {
	}

	private Listing listing(String productKey) throws SQLException {
		try (
				var connection = dataSource.getConnection();
				var statement = connection.prepareStatement("""
						SELECT l.price, l.currency, l.active, l.last_seen_run_id
						FROM IN_LISTING l JOIN IN_RETAILER_PRODUCT p ON p.id = l.retailer_product_id
						WHERE p.product_key = ? AND l.catalogue_id = ?
						""")
		) {
			statement.setString(1, productKey);
			statement.setString(2, CATALOGUE);
			try (var resultSet = statement.executeQuery()) {
				resultSet.next();
				return new Listing(resultSet.getBigDecimal(1), resultSet.getString(2), resultSet.getBoolean(3), resultSet.getString(4));
			}
		}
	}

	private Timestamp timestamp(String runId, String column) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT " + column + " FROM IN_INGESTION_RUN WHERE id = ?::uuid")) {
			statement.setString(1, runId);
			try (var resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getTimestamp(1);
			}
		}
	}

	private void execute(String sql) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}
}
