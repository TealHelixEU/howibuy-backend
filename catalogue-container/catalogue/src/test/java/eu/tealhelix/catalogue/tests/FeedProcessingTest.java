package eu.tealhelix.catalogue.tests;

import static eu.tealhelix.catalogue.tests.TestFeeds.awaitTheEndOf;
import static eu.tealhelix.catalogue.tests.TestFeeds.counts;
import static eu.tealhelix.catalogue.tests.TestFeeds.feed;
import static eu.tealhelix.catalogue.tests.TestFeeds.product;
import static eu.tealhelix.catalogue.tests.TestFeeds.rejects;
import static eu.tealhelix.catalogue.tests.TestFeeds.timestamp;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.SQLException;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What processing a Feed does to the Retailer Products and Listings of its Catalogue, in the database: new, changed and
 * unchanged products, the change sequence, the classification input hash, the names merged across languages, the rejects,
 * the order of the runs and the recovery of an interrupted run. Lime Fresh pushes the Feeds to a {@link TestCatalogue}
 * of each test's own.
 */
@QuarkusTest
@WithCatalogueDb
@TestSecurity(user = "lime_fresh", roles = "retailer")
@OidcSecurity(claims = {@Claim(key = "sub", value = TestCatalogue.LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
class FeedProcessingTest {
	@Inject
	DataSource dataSource;

	@Inject
	FeedStore feedStore;

	@Inject
	IngestionRunDispatcher dispatcher;

	private TestCatalogue catalogue;

	@BeforeEach
	void createTheCatalogueOfTheTest() throws SQLException {
		catalogue = TestCatalogue.create(dataSource);
	}

	@AfterEach
	void removeTheCatalogueOfTheTest() throws SQLException {
		catalogue.remove();
	}

	@Test
	void tellsNewChangedAndUnchangedProductsApartAcrossTwoRuns() throws Exception {
		var first = process("SNAPSHOT", feed(
				product(key("same"), Map.of("el", "Γάλα"), "1.29"),
				product(key("renamed"), Map.of("el", "Τυρί"), "4.50"),
				product(key("repriced"), Map.of("el", "Ψωμί"), "0.99")
		));
		assertThat(counts(first)).isEqualTo(Map.of("total", 3, "valid", 3, "rejected", 0, "added", 3, "changed", 0, "unchanged", 0, "missing", 0, "deactivated", 0));
		var before = catalogue.changeSequences();

		var second = process("SNAPSHOT", feed(
				product(key("same"), Map.of("el", "Γάλα"), "1.29"),
				product(key("renamed"), Map.of("el", "Τυρί φέτα"), "4.50"),
				product(key("repriced"), Map.of("el", "Ψωμί"), "1.09"),
				product(key("new"), Map.of("el", "Αυγά"), "2.10")
		));

		assertThat(counts(second)).isEqualTo(Map.of("total", 4, "valid", 4, "rejected", 0, "added", 1, "changed", 2, "unchanged", 1, "missing", 0, "deactivated", 0));
		var after = catalogue.changeSequences();
		assertThat(after.get(key("same"))).as("an unchanged product keeps its stamp").isEqualTo(before.get(key("same")));
		var latestBefore = before.values().stream().mapToLong(Long::longValue).max().orElseThrow();
		assertThat(after.get(key("renamed"))).as("a changed product is stamped anew").isGreaterThan(latestBefore);
		assertThat(after.get(key("repriced"))).as("a changed price stamps its product anew").isGreaterThan(latestBefore);
		assertThat(after.get(key("new"))).as("a new product is stamped").isGreaterThan(latestBefore);
		assertThat(catalogue.listing(key("repriced"))).isEqualTo(new TestCatalogue.Listing(new BigDecimal("1.0900"), "EUR", true, second));
		assertThat(catalogue.listing(key("same"))).as("an unchanged product is seen by the latest run").isEqualTo(new TestCatalogue.Listing(new BigDecimal("1.2900"), "EUR", true, second));
	}

	@Test
	void aPriceOnlyChangeStampsTheProductButKeepsTheHash() throws Exception {
		process("DELTA", feed(product(key("milk"), Map.of("el", "Γάλα"), Map.of("fat", "3.5%"), List.of("dairy"), "1.29")));
		var hash = catalogue.inputHash(key("milk"));
		var stamp = catalogue.changeSequences().get(key("milk"));

		process("DELTA", feed(product(key("milk"), Map.of("el", "Γάλα"), Map.of("fat", "3.5%"), List.of("dairy"), "1.19")));

		assertThat(catalogue.inputHash(key("milk"))).isEqualTo(hash);
		assertThat(catalogue.changeSequences().get(key("milk"))).isGreaterThan(stamp);
	}

	@Test
	void theNamesOfARecordReplaceThoseOfItsLanguagesOnly() throws Exception {
		process("DELTA", feed(product(key("milk"), Map.of("el", "Γάλα", "en", "Milk"), "1.29")));
		var hash = catalogue.inputHash(key("milk"));

		process("DELTA", feed(product(key("milk"), Map.of("el", "Γάλα αγελάδας"), "1.29")));

		assertThat(catalogue.names(key("milk"))).isEqualTo(Map.of("el", "Γάλα αγελάδας", "en", "Milk"));
		assertThat(catalogue.inputHash(key("milk"))).isNotEqualTo(hash);
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

		assertThat(counts(runId)).isEqualTo(Map.of("total", 5, "valid", 1, "rejected", 4, "added", 1, "changed", 0, "unchanged", 0, "missing", 0, "deactivated", 0));
		assertThat(rejects(runId)).containsExactly(
				Map.of("position", 2, "reason", "The record is not a JSON object"),
				Map.of("position", 3, "productKey", key("no-price"), "reason", "The price is missing"),
				Map.of("position", 4, "productKey", key("german"), "reason", "The Catalogue does not accept the language, language: de"),
				Map.of("position", 5, "productKey", key("free"), "reason", "The price is not positive, price: 0")
		);
		assertThat(catalogue.changeSequences()).containsOnlyKeys(key("valid"));
	}

	@Test
	void theRunsOfACatalogueAreProcessedOneAfterTheOtherInOrderOfReceipt() throws Exception {
		var first = send("DELTA", feed(product(key("milk"), Map.of("el", "Γάλα"), "1.29"), product(key("bread"), Map.of("el", "Ψωμί"), "0.99")));
		var second = send("DELTA", feed(product(key("milk"), Map.of("el", "Γάλα"), "1.19")));

		assertThat(awaitTheEndOf(dataSource, first)).isEqualTo("DONE");
		assertThat(awaitTheEndOf(dataSource, second)).isEqualTo("DONE");

		assertThat(catalogue.listing(key("milk")).price()).isEqualByComparingTo("1.19");
		assertThat(timestamp(dataSource, second, "started_at")).isAfterOrEqualTo(timestamp(dataSource, first, "finished_at"));
	}

	@Test
	void aRunInterruptedByAStopIsProcessedAgainWhenTheIngestionStarts() throws Exception {
		var runId = UUID.randomUUID().toString();
		var storedFeed = feedStore.store(new CatalogueIdImpl(catalogue.id()), new IngestionRunIdImpl(runId), new ByteArrayInputStream(feed(
				product(key("milk"), Map.of("el", "Γάλα"), "1.29"),
				"not json"
		).getBytes(UTF_8)));
		execute("INSERT INTO IN_INGESTION_RUN (id, catalogue_id, feed_type, status, adapter, adapter_version, received_at, started_at, file_path, file_size, file_sha256) VALUES "
				+ "('" + runId + "', '" + catalogue.id() + "', 'DELTA', 'PROCESSING', 'test-jsonl', '1', now(), now(), '" + storedFeed.path() + "', " + storedFeed.size() + ", '" + storedFeed.sha256() + "')");
		execute("INSERT INTO IN_INGESTION_REJECT (ingestion_run_id, position, product_key, reason) VALUES ('" + runId + "', 2, NULL, 'The record is not a JSON object')");

		dispatcher.recover();

		assertThat(awaitTheEndOf(dataSource, runId)).isEqualTo("DONE");
		assertThat(counts(runId)).isEqualTo(Map.of("total", 2, "valid", 1, "rejected", 1, "added", 1, "changed", 0, "unchanged", 0, "missing", 0, "deactivated", 0));
		assertThat(rejects(runId)).containsExactly(Map.of("position", 2, "reason", "The record is not a JSON object"));
		assertThat(catalogue.changeSequences()).containsOnlyKeys(key("milk"));
	}

	private String key(String name) {
		return catalogue.key(name);
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
				.when().post("/api/catalogue/v1/catalogues/" + catalogue.id() + "/feeds?type=" + feedType)
				.then()
				.statusCode(202)
				.extract().jsonPath().getString("id");
	}

	private void execute(String sql) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}
}
