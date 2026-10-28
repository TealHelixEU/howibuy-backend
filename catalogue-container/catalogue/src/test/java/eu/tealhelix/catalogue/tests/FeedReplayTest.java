package eu.tealhelix.catalogue.tests;

import static eu.tealhelix.catalogue.tests.TestFeeds.awaitTheEndOf;
import static eu.tealhelix.catalogue.tests.TestFeeds.counts;
import static eu.tealhelix.catalogue.tests.TestFeeds.deletion;
import static eu.tealhelix.catalogue.tests.TestFeeds.feed;
import static eu.tealhelix.catalogue.tests.TestFeeds.product;
import static eu.tealhelix.catalogue.tests.TestFeeds.run;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.services.v1.FeedService;
import eu.tealhelix.catalogue.services.v1.impl.FeedStore;
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
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An operator replays a stored Feed, in the database: a new run processes the Feed again with the adapter the Catalogue
 * has now, and the run replayed stays as it was. Lime Fresh pushes the Feeds through the service to a
 * {@link TestCatalogue} of each test's own, whose adapter is the {@link FixableFeedReader}; the operator replays them
 * through the endpoint.
 */
@QuarkusTest
@WithCatalogueDb
@TestSecurity(user = "operator", roles = "operator")
@OidcSecurity(claims = @Claim(key = "sub", value = FeedDeactivationTest.OPERATOR))
class FeedReplayTest {
	private static final User LIME_FRESH = new UserImpl(new UserIdImpl(TestCatalogue.LIME_FRESH), null, null, false, false, Set.of(Role.RETAILER));

	@Inject
	DataSource dataSource;

	@Inject
	FeedService feedService;

	@Inject
	FeedStore feedStore;

	@Inject
	FixableFeedReader adapter;

	private TestCatalogue catalogue;

	@BeforeEach
	void createTheCatalogueOfTheTest() throws SQLException {
		adapter.setBroken(false);
		catalogue = TestCatalogue.create(dataSource, FixableFeedReader.NAME);
	}

	@AfterEach
	void removeTheCatalogueOfTheTest() throws SQLException {
		adapter.setBroken(false);
		catalogue.remove();
	}

	@Test
	void aRunThatFailedUnderABrokenAdapterSucceedsOnReplayUnderTheFixedOne() throws Exception {
		adapter.setBroken(true);
		var failed = send("SNAPSHOT", products(3));
		assertThat(awaitTheEndOf(dataSource, failed)).isEqualTo("FAILED");
		var asFailed = run(failed).prettify();
		adapter.setBroken(false);

		var response = given().when().post("/api/catalogue/v1/ingestion-runs/" + failed + "/replay").then().statusCode(202).extract();

		var replay = response.jsonPath();
		var replayId = replay.getString("id");
		assertThat(replayId).isNotEqualTo(failed);
		assertThat(response.header("Location")).endsWith("/api/catalogue/v1/ingestion-runs/" + replayId);
		assertThat(replay.getString("status")).isEqualTo("RECEIVED");
		assertThat(replay.getString("replayOfRunId")).isEqualTo(failed);
		assertThat(awaitTheEndOf(dataSource, replayId)).isEqualTo("DONE");
		var done = run(replayId);
		assertThat(done.getString("adapter")).isEqualTo(FixableFeedReader.NAME);
		assertThat(done.getString("adapterVersion")).isEqualTo(FixableFeedReader.FIXED);
		assertThat(done.getString("feedId")).as("the Feed").isEqualTo(replay.getString("feedId")).isEqualTo(run(failed).getString("feedId"));
		assertThat(done.getString("receivedAt")).as("the Feed's receipt").isEqualTo(replay.getString("receivedAt")).isEqualTo(run(failed).getString("receivedAt"));
		assertThat(counts(replayId)).isEqualTo(Map.of("total", 3, "valid", 3, "rejected", 0, "added", 3, "changed", 0, "unchanged", 0, "missing", 0, "deactivated", 0));
		assertThat(catalogue.listing(key(2))).isEqualTo(new TestCatalogue.Listing(new BigDecimal("1.2900"), "EUR", true, replayId));
		assertThat(catalogue.names(key(2))).isEqualTo(Map.of("el", "Γάλα 2"));
		var original = run(failed);
		assertThat(original.prettify()).as("the run replayed stays as it was").isEqualTo(asFailed);
		assertThat(original.getString("adapterVersion")).isEqualTo(FixableFeedReader.BROKEN);
		assertThat(original.getString("error")).isEqualTo(FixableFeedReader.FAULT);
	}

	@Test
	void aSnapshotThatALaterSnapshotSupersedesIsNotReplayed() throws Exception {
		var first = process("SNAPSHOT", products(10));
		var later = process("SNAPSHOT", products(9));

		var conflict = given().when().post("/api/catalogue/v1/ingestion-runs/" + first + "/replay").then().statusCode(409).extract().jsonPath();

		assertThat(conflict.getString("message")).isEqualTo("A later Snapshot of the Catalogue supersedes the Ingestion Run, runId: " + first);
		assertThat(catalogue.listing(key(9)).active()).as("nothing is put back on sale").isFalse();
		assertThat(awaitTheEndOf(dataSource, replay(later))).isEqualTo("DONE");
	}

	@Test
	void aSnapshotIsReplayedOverTheDeltasAndDeletionsThatFollowedIt() throws Exception {
		var snapshot = process("SNAPSHOT", products(3));
		process("DELETION", feed(deletion(key(0))));
		process("DELTA", feed(product(key(1), Map.of("el", "Γάλα 1"), "1.19")));

		var replay = replay(snapshot);

		assertThat(awaitTheEndOf(dataSource, replay)).isEqualTo("DONE");
		assertThat(catalogue.listing(key(0))).as("the Feed replayed applies again").isEqualTo(new TestCatalogue.Listing(new BigDecimal("1.2900"), "EUR", true, replay));
		assertThat(catalogue.listing(key(1)).price()).isEqualByComparingTo("1.29");
	}

	@Test
	void anEarlierDeltaOrDeletionFeedIsReplayed() throws Exception {
		var delta = process("DELTA", products(2));
		var deletion = process("DELETION", feed(deletion(key(0))));
		process("SNAPSHOT", products(2));

		var deltaReplay = replay(delta);
		assertThat(awaitTheEndOf(dataSource, deltaReplay)).isEqualTo("DONE");
		var deletionReplay = replay(deletion);
		assertThat(awaitTheEndOf(dataSource, deletionReplay)).isEqualTo("DONE");

		assertThat(counts(deletionReplay).get("deactivated")).isEqualTo(1);
		assertThat(catalogue.listing(key(0)).active()).isFalse();
		assertThat(catalogue.listing(key(1)).active()).isTrue();
	}

	@Test
	void aReplayIsReplayedOnTheSameFeed() throws Exception {
		adapter.setBroken(true);
		var failed = send("DELTA", products(1));
		assertThat(awaitTheEndOf(dataSource, failed)).isEqualTo("FAILED");
		var replay = replay(failed);
		assertThat(awaitTheEndOf(dataSource, replay)).isEqualTo("FAILED");
		adapter.setBroken(false);

		var again = replay(replay);

		assertThat(awaitTheEndOf(dataSource, again)).isEqualTo("DONE");
		assertThat(run(again).getString("replayOfRunId")).isEqualTo(replay);
		assertThat(catalogue.listing(key(0)).lastSeenRunId()).isEqualTo(again);
	}

	@Test
	void aRunWhoseFeedIsNoLongerStoredIsNotReplayed() throws Exception {
		var done = process("DELTA", products(1));
		feedStore.delete(filePath(done));

		var notFound = given().when().post("/api/catalogue/v1/ingestion-runs/" + done + "/replay").then().statusCode(404).extract().jsonPath();

		assertThat(notFound.getString("message")).isEqualTo("The Feed of the Ingestion Run is no longer stored, runId: " + done);
	}

	@Test
	void onlyAFinishedRunIsReplayed() throws Exception {
		process("SNAPSHOT", products(10));
		var held = send("SNAPSHOT", products(8));
		assertThat(awaitTheEndOf(dataSource, held)).isEqualTo("HELD");

		var conflict = given().when().post("/api/catalogue/v1/ingestion-runs/" + held + "/replay").then().statusCode(409).extract().jsonPath();

		assertThat(conflict.getString("message")).isEqualTo("Only a finished Ingestion Run is replayed, status: HELD, runId: " + held);
		given().when().post("/api/catalogue/v1/ingestion-runs/" + UUID.randomUUID() + "/replay").then().statusCode(404);
		given().when().post("/api/catalogue/v1/ingestion-runs/not-a-uuid/replay").then().statusCode(400);
		given().when().post("/api/catalogue/v1/ingestion-runs/" + held + "/discard").then().statusCode(200);
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = TestCatalogue.LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aRetailerReplaysNoRunNotEvenItsOwn() throws Exception {
		var done = process("DELTA", products(1));

		given().when().post("/api/catalogue/v1/ingestion-runs/" + done + "/replay").then().statusCode(403);
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

	private String filePath(String runId) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT f.file_path FROM IN_INGESTION_RUN r JOIN IN_FEED f ON f.id = r.feed_id WHERE r.id = ?::uuid")) {
			statement.setString(1, runId);
			try (var resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getString(1);
			}
		}
	}

	/**
	 * Replay a run as the operator.
	 *
	 * @return The id of the new run
	 */
	private static String replay(String runId) {
		JsonPath replay = given().when().post("/api/catalogue/v1/ingestion-runs/" + runId + "/replay").then().statusCode(202).extract().jsonPath();
		return replay.getString("id");
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
