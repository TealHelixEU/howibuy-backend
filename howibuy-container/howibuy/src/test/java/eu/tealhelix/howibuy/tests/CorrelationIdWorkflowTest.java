package eu.tealhelix.howibuy.tests;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;
import jakarta.inject.Inject;

import eu.tealhelix.common.services.generic.UserService;
import eu.tealhelix.common.test.quarkus.InjectKeycloak;
import eu.tealhelix.common.test.quarkus.InjectPostgres;
import eu.tealhelix.common.test.quarkus.ServiceAccount;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@QuarkusTest
@WithRetailerIdm
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CorrelationIdWorkflowTest {
	private static final long ASYNC_WAIT_SECONDS = 300;
	private static final String CORRELATION_ID1 = "ABCDE";

	/** The Catalogue of the realm's development retailer, Lime Fresh, which the {@code dev} context seeds. */
	private static final String CATALOGUE_ID = "lime-fresh-gr";

	@InjectPostgres
	private PostgreSQLContainer postgres;

	@InjectKeycloak
	private GenericContainer<?> keycloak;

	@Inject
	UserService userService;

	@Test
	@Order(1)
	void testNewCorrelationId() {
		String serviceAccessToken = ServiceAccount.RETAILER.accessToken(keycloak);

		String impersonationToken = RestAssured
				.given()
				.header("Authorization", "Bearer " + serviceAccessToken)
				.header("Content-Type", "application/json")
				.body(tokenExchangeRequest(CORRELATION_ID1, CATALOGUE_ID))
				.when()
				.post("/api/howibuy/v1/tokenexchange")
				.then()
				.statusCode(200)
				.contentType(JSON)
				.extract()
				.path("access_token");

		String userId = GreetingPage.userId(impersonationToken);

		userService.requireUserWithId(new UserIdImpl(userId), "name")
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	/**
	 * The IDM authenticates every client of the realm; only those holding the retailer role may act for users.
	 */
	@Test
	@Order(2)
	void testTokenExchangeIsRefusedToAServiceAccountThatIsNoRetailer() {
		RestAssured
				.given()
				.header("Authorization", "Bearer " + ServiceAccount.CATALOGUE_SYNC.accessToken(keycloak))
				.header("Content-Type", "application/json")
				.body(tokenExchangeRequest(CORRELATION_ID1, CATALOGUE_ID))
				.when()
				.post("/api/howibuy/v1/tokenexchange")
				.then()
				.statusCode(403);
	}

	/**
	 * The token exchange is how a retailer has the products of one of its Catalogues assessed for a user, so it must name
	 * that Catalogue.
	 */
	@Test
	@Order(3)
	void testTokenExchangeWithoutACatalogueIsMalformed() {
		RestAssured
				.given()
				.header("Authorization", "Bearer " + ServiceAccount.RETAILER.accessToken(keycloak))
				.header("Content-Type", "application/json")
				.body("{\"correlationId\": \"" + CORRELATION_ID1 + "\"}")
				.when()
				.post("/api/howibuy/v1/tokenexchange")
				.then()
				.statusCode(400);
	}

	@Test
	@Order(4)
	void testTokenExchangeWithoutABodyIsMalformed() {
		RestAssured
				.given()
				.header("Authorization", "Bearer " + ServiceAccount.RETAILER.accessToken(keycloak))
				.header("Content-Type", "application/json")
				.when()
				.post("/api/howibuy/v1/tokenexchange")
				.then()
				.statusCode(400)
				.body("message", equalTo("Required input 'body' is missing."));
	}

	@Test
	@Order(5)
	void testTokenExchangeIsRefusedInACatalogueThatIsNotTheRetailers() {
		RestAssured
				.given()
				.header("Authorization", "Bearer " + ServiceAccount.RETAILER.accessToken(keycloak))
				.header("Content-Type", "application/json")
				.body(tokenExchangeRequest(CORRELATION_ID1, "no-such-catalogue"))
				.when()
				.post("/api/howibuy/v1/tokenexchange")
				.then()
				.statusCode(403);
	}

	private static String tokenExchangeRequest(String correlationId, String catalogueId) {
		return "{\"correlationId\": \"" + correlationId + "\", \"catalogueId\": \"" + catalogueId + "\"}";
	}
}
