package eu.tealhelix.catalogue.tests;

import static eu.tealhelix.catalogue.tests.TestFeeds.awaitTheEndOf;
import static eu.tealhelix.catalogue.tests.TestFeeds.counts;
import static eu.tealhelix.catalogue.tests.TestFeeds.deletion;
import static eu.tealhelix.catalogue.tests.TestFeeds.feed;
import static eu.tealhelix.catalogue.tests.TestFeeds.product;
import static eu.tealhelix.catalogue.tests.TestFeeds.rejects;
import static eu.tealhelix.catalogue.tests.TestFeeds.run;
import static eu.tealhelix.catalogue.tests.TestFeeds.timestamp;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.services.v1.FeedService;
import eu.tealhelix.catalogue.test.quarkus.WithCatalogueDb;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.v1.model.Role;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What a Feed does to the Listings of its Catalogue that it does not carry, in the database. A Snapshot takes them off
 * sale, unless they are more than the hold share of the Catalogue's active Listings (10%): the run is then held, and the
 * later runs of the Catalogue wait, until an operator confirms or discards it. A Delta takes nothing off sale, a Deletion
 * Feed takes its products off sale, and a Listing taken off sale is back on sale with the next Feed that carries it. Lime
 * Fresh pushes the Feeds through the service to a {@link TestCatalogue} of each test's own; the operator decides on the
 * held runs through the endpoints.
 */
@QuarkusTest
@WithCatalogueDb
@TestSecurity(user = "operator", roles = "operator")
@OidcSecurity(claims = @Claim(key = "sub", value = FeedDeactivationTest.OPERATOR))
class FeedDeactivationTest {
	static final String OPERATOR = "2e788895-0503-4777-a7bd-24e5d61db5b1";
	private static final User LIME_FRESH = new UserImpl(new UserIdImpl(TestCatalogue.LIME_FRESH), null, null, false, false, Set.of(Role.RETAILER));

	@Inject
	DataSource dataSource;

	@Inject
	FeedService feedService;

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
	void aSnapshotTakesTheListingsItDoesNotCarryOffSale() throws Exception {
		var first = process("SNAPSHOT", products(10));
		var latestStamp = Collections.max(catalogue.changeSequences().values());

		var second = process("SNAPSHOT", products(9));

		assertThat(counts(second)).isEqualTo(countsOfValid(9, 0, 0, 9, 1, 1));
		assertThat(catalogue.listing(key(9)).active()).isFalse();
		assertThat(catalogue.listing(key(9)).lastSeenRunId()).as("when it was last seen stays").isEqualTo(first);
		assertThat(catalogue.changeSequences().get(key(9))).as("a product taken off sale is stamped anew").isGreaterThan(latestStamp);
		assertThat(catalogue.listing(key(8))).isEqualTo(new TestCatalogue.Listing(new BigDecimal("1.2900"), "EUR", true, second));
	}

	@Test
	void aSnapshotThatWouldTakeTooMuchOffSaleIsHeldUntilAnOperatorConfirmsIt() throws Exception {
		process("SNAPSHOT", products(10));
		var held = send("SNAPSHOT", feed(IntStream.range(0, 8).mapToObj(i -> product(key(i), Map.of("el", i == 0 ? "Τυρί" : "Γάλα " + i), "1.29")).toArray(String[]::new)));

		assertThat(awaitTheEndOf(dataSource, held)).isEqualTo("HELD");
		assertThat(counts(held)).isEqualTo(countsOfValid(8, 0, 1, 7, 2, 0));
		assertThat(run(held).getString("finishedAt")).isNull();
		assertThat(catalogue.listing(key(8)).active()).isTrue();
		assertThat(catalogue.names(key(0))).as("the records are applied before the hold").isEqualTo(Map.of("el", "Τυρί"));
		var waiting = send("DELTA", feed(product(key(0), Map.of("el", "Τυρί"), "1.19")));

		var confirmed = given().when().post("/api/catalogue/v1/ingestion-runs/" + held + "/confirmation").then().statusCode(200).extract().jsonPath();

		assertThat(confirmed.getString("status")).isEqualTo("DONE");
		assertThat(confirmed.getString("finishedAt")).isNotNull();
		assertThat(confirmed.getMap("counts", String.class, Integer.class)).isEqualTo(countsOfValid(8, 0, 1, 7, 2, 2));
		assertThat(catalogue.listing(key(8)).active()).isFalse();
		assertThat(catalogue.listing(key(9)).active()).isFalse();
		assertThat(catalogue.listing(key(7)).active()).isTrue();
		assertThat(awaitTheEndOf(dataSource, waiting)).isEqualTo("DONE");
		assertThat(timestamp(dataSource, waiting, "started_at")).as("the later run waits for the decision").isAfterOrEqualTo(timestamp(dataSource, held, "finished_at"));
	}

	@Test
	void aHeldSnapshotThatAnOperatorDiscardsTakesNothingOffSale() throws Exception {
		process("SNAPSHOT", products(10));
		var held = send("SNAPSHOT", feed(IntStream.range(0, 8).mapToObj(i -> product(key(i), Map.of("el", i == 0 ? "Τυρί" : "Γάλα " + i), "1.29")).toArray(String[]::new)));
		assertThat(awaitTheEndOf(dataSource, held)).isEqualTo("HELD");
		var waiting = send("DELTA", feed(product(key(0), Map.of("el", "Τυρί"), "1.19")));

		var discarded = given().when().post("/api/catalogue/v1/ingestion-runs/" + held + "/discard").then().statusCode(200).extract().jsonPath();

		assertThat(discarded.getString("status")).isEqualTo("DISCARDED");
		assertThat(discarded.getString("finishedAt")).isNotNull();
		assertThat(discarded.getMap("counts", String.class, Integer.class)).isEqualTo(countsOfValid(8, 0, 1, 7, 2, 0));
		assertThat(catalogue.listing(key(8)).active()).isTrue();
		assertThat(catalogue.listing(key(9)).active()).isTrue();
		assertThat(catalogue.names(key(0))).as("the records applied before the hold are kept").isEqualTo(Map.of("el", "Τυρί"));
		assertThat(awaitTheEndOf(dataSource, waiting)).isEqualTo("DONE");
	}

	@Test
	void onlyAHeldRunIsConfirmedOrDiscarded() throws Exception {
		var done = process("SNAPSHOT", products(1));

		given().when().post("/api/catalogue/v1/ingestion-runs/" + done + "/confirmation").then().statusCode(409);
		var conflict = given().when().post("/api/catalogue/v1/ingestion-runs/" + done + "/discard").then().statusCode(409).extract().jsonPath();
		assertThat(conflict.getString("message")).isEqualTo("The Ingestion Run is not held, status: DONE, runId: " + done);
		given().when().post("/api/catalogue/v1/ingestion-runs/" + UUID.randomUUID() + "/confirmation").then().statusCode(404);
		given().when().post("/api/catalogue/v1/ingestion-runs/" + UUID.randomUUID() + "/discard").then().statusCode(404);
		given().when().post("/api/catalogue/v1/ingestion-runs/not-a-uuid/confirmation").then().statusCode(400);
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = TestCatalogue.LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aRetailerDecidesOnNoHeldRunNotEvenItsOwn() throws Exception {
		process("SNAPSHOT", products(10));
		var held = send("SNAPSHOT", products(8));
		assertThat(awaitTheEndOf(dataSource, held)).isEqualTo("HELD");

		given().when().post("/api/catalogue/v1/ingestion-runs/" + held + "/confirmation").then().statusCode(403);
		given().when().post("/api/catalogue/v1/ingestion-runs/" + held + "/discard").then().statusCode(403);

		assertThat(run(held).getString("status")).isEqualTo("HELD");
	}

	@Test
	void aDeltaTakesNothingOffSale() throws Exception {
		process("SNAPSHOT", products(3));

		var delta = process("DELTA", feed(product(key(0), Map.of("el", "Γάλα 0"), "1.19")));

		assertThat(counts(delta)).isEqualTo(countsOfValid(1, 0, 1, 0, 0, 0));
		assertThat(catalogue.listing(key(1)).active()).isTrue();
		assertThat(catalogue.listing(key(2)).active()).isTrue();
	}

	@Test
	void aDeletionFeedTakesItsProductsOffSaleInItsCatalogue() throws Exception {
		var snapshot = process("SNAPSHOT", products(3));
		var stamps = catalogue.changeSequences();
		var latestStamp = Collections.max(stamps.values());

		var deletion = process("DELETION", feed(deletion(key(0)), deletion(catalogue.key("unknown")), "not json", deletion(key(0))));

		assertThat(counts(deletion)).isEqualTo(Map.of("total", 4, "valid", 2, "rejected", 2, "added", 0, "changed", 0, "unchanged", 1, "missing", 0, "deactivated", 1));
		assertThat(rejects(deletion)).containsExactly(
				Map.of("position", 2, "productKey", catalogue.key("unknown"), "reason", "The Catalogue has no Listing of the product"),
				Map.of("position", 3, "reason", "The record is not a JSON object")
		);
		assertThat(catalogue.listing(key(0)).active()).isFalse();
		assertThat(catalogue.listing(key(0)).lastSeenRunId()).as("when it was last seen stays").isEqualTo(snapshot);
		assertThat(catalogue.changeSequences().get(key(0))).isGreaterThan(latestStamp);
		assertThat(catalogue.listing(key(1)).active()).isTrue();
		assertThat(catalogue.changeSequences().get(key(1))).isEqualTo(stamps.get(key(1)));
	}

	@Test
	void aListingTakenOffSaleIsBackOnSaleWithTheSameHash() throws Exception {
		process("SNAPSHOT", products(2));
		process("DELETION", feed(deletion(key(1))));
		var hash = catalogue.inputHash(key(1));
		var stamp = catalogue.changeSequences().get(key(1));

		var again = process("SNAPSHOT", products(2));

		assertThat(counts(again)).isEqualTo(countsOfValid(2, 0, 1, 1, 0, 0));
		assertThat(catalogue.listing(key(1)).active()).isTrue();
		assertThat(catalogue.listing(key(1)).lastSeenRunId()).isEqualTo(again);
		assertThat(catalogue.changeSequences().get(key(1))).as("a reactivation is stamped").isGreaterThan(stamp);
		assertThat(catalogue.inputHash(key(1))).as("so that no reclassification follows").isEqualTo(hash);
	}

	private String key(int product) {
		return catalogue.key("p" + product);
	}

	/**
	 * A Feed of the products {@code 0} to {@code count - 1}, each with the name and the price it always has.
	 */
	private String products(int count) {
		return feed(IntStream.range(0, count).mapToObj(i -> product(key(i), Map.of("el", "Γάλα " + i), "1.29")).toArray(String[]::new));
	}

	/**
	 * The counts of a run of valid records only.
	 */
	private static Map<String, Integer> countsOfValid(int total, int added, int changed, int unchanged, int missing, int deactivated) {
		return Map.of("total", total, "valid", total, "rejected", 0, "added", added, "changed", changed, "unchanged", unchanged, "missing", missing, "deactivated", deactivated);
	}

	/**
	 * Push a Feed as Lime Fresh and wait until its run is done.
	 *
	 * @return The id of the run
	 */
	private String process(String feedType, String feed) throws SQLException, InterruptedException {
		var runId = send(feedType, feed);
		assertThat(awaitTheEndOf(dataSource, runId)).isEqualTo("DONE");
		return runId;
	}

	private String send(String feedType, String feed) {
		var received = feedService.receive(LIME_FRESH, new CatalogueIdImpl(catalogue.id()), FeedType.valueOf(feedType), new ByteArrayInputStream(feed.getBytes(UTF_8)));
		return received.runs().getFirst().getId().asString();
	}
}
