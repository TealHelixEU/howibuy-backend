package eu.tealhelix.catalogue.tests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import eu.tealhelix.catalogue.test.quarkus.WithCatalogueDbAndKeycloak;
import eu.tealhelix.common.test.quarkus.InjectKeycloak;
import eu.tealhelix.common.test.quarkus.ServiceAccount;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * End to end with a real Keycloak: a Retailer's client pushes a Feed with the access token its backend obtains, and
 * reads its Ingestion Run back.
 */
@QuarkusTest
@WithCatalogueDbAndKeycloak
class FeedWorkflowTest {
	@InjectKeycloak
	GenericContainer<?> keycloak;

	@Test
	void aRetailerPushesAFeedAndReadsItsRunBack() {
		var accessToken = ServiceAccount.RETAILER.accessToken(keycloak);

		var statusUrl = given()
				.header("Authorization", "Bearer " + accessToken)
				.contentType("application/xml")
				.body("<products/>")
				.when().post("/api/catalogue/v1/catalogues/lime-fresh-gr/feeds?type=DELTA")
				.then()
				.statusCode(202)
				.extract().header("Location");

		var status = given()
				.header("Authorization", "Bearer " + accessToken)
				.when().get(statusUrl)
				.then()
				.statusCode(200)
				.extract().jsonPath().getString("status");

		assertThat(status).isEqualTo("RECEIVED");
	}
}
