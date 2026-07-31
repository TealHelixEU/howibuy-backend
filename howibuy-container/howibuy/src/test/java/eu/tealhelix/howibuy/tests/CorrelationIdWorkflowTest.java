package eu.tealhelix.howibuy.tests;

import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.regex.Pattern;
import jakarta.inject.Inject;

import eu.tealhelix.common.services.generic.UserService;
import eu.tealhelix.common.test.quarkus.InjectKeycloak;
import eu.tealhelix.common.test.quarkus.InjectPostgres;
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

	@InjectPostgres
	private PostgreSQLContainer postgres;

	@InjectKeycloak
	private GenericContainer<?> keycloak;

	@Inject
	UserService userService;

	@Test
	@Order(1)
	void testNewCorrelationId() {
		String serviceAccessToken = RetailerServiceAccount.accessToken(keycloak);

		String impersonationToken = RestAssured
				.given()
				.header("Authorization", "Bearer " + serviceAccessToken)
				.header("Content-Type", "application/json")
				.body("{\"correlationId\": \"" + CORRELATION_ID1 + "\"}")
				.when()
				.post("/api/howibuy/v1/tokenexchange")
				.then()
				.statusCode(200)
				.contentType(JSON)
				.extract()
				.path("access_token");

		String responseBody = RestAssured
				.given()
				.header("Authorization", "Bearer " + impersonationToken)
				.header("Accepts", "text/html")
				.when()
				.get("/api/howibuy/v1/greeting")
				.then()
				.statusCode(200)
				.extract()
				.body()
				.asString();

		var m = Pattern.compile("<h1>Hello (.*)!</h1>", Pattern.MULTILINE).matcher(responseBody);
		assertTrue(m.find());
		String userId = m.group(1);

		userService.requireUserWithId(new UserIdImpl(userId), "name", false)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}
}
