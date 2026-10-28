package eu.tealhelix.catalogue.tests;

import static eu.tealhelix.catalogue.tests.TestFeeds.awaitTheEndOf;
import static eu.tealhelix.catalogue.tests.TestFeeds.feed;
import static eu.tealhelix.catalogue.tests.TestFeeds.product;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.test.quarkus.WithCatalogueDbAndKeycloak;
import eu.tealhelix.common.test.quarkus.InjectKeycloak;
import eu.tealhelix.common.test.quarkus.ServiceAccount;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * End to end with a real Keycloak: a Retailer's client pushes a small Snapshot with the access token its backend gets
 * and reads back its Feed, with the Ingestion Run processed.
 */
@QuarkusTest
@WithCatalogueDbAndKeycloak
class FeedWorkflowTest {
	@InjectKeycloak
	GenericContainer<?> keycloak;

	@Inject
	DataSource dataSource;

	@Test
	void aRetailerPushesASnapshotAndReadsItsRunBackDone() throws Exception {
		var accessToken = ServiceAccount.RETAILER.accessToken(keycloak);

		var receipt = given()
				.header("Authorization", "Bearer " + accessToken)
				.contentType("application/x-ndjson")
				.body(feed(
						product("workflow-1", Map.of("el", "Γάλα", "en", "Milk"), "1.29"),
						product("workflow-2", Map.of("el", "Ψωμί"), "0.99")
				).getBytes(UTF_8))
				.when().post("/api/catalogue/v1/feeds/catalogues/lime-fresh-gr?type=SNAPSHOT")
				.then()
				.statusCode(202)
				.extract();
		awaitTheEndOf(dataSource, receipt.jsonPath().getString("runs[0].id"));

		var feed = given()
				.header("Authorization", "Bearer " + accessToken)
				.when().get(receipt.header("Location"))
				.then()
				.statusCode(200)
				.extract().jsonPath();

		assertThat(feed.getString("runs[0].status")).isEqualTo("DONE");
		assertThat(feed.getMap("runs[0].counts", String.class, Integer.class))
				.isEqualTo(Map.of("total", 2, "valid", 2, "rejected", 0, "added", 2, "changed", 0, "unchanged", 0, "missing", 0, "deactivated", 0));
	}
}
