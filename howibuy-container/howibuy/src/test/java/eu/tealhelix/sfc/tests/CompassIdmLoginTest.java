package eu.tealhelix.sfc.tests;

import static eu.tealhelix.common.test.quarkus.PostgresAndKeycloakTestResource.KEYCLOAK_PORT;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import com.nimbusds.jwt.SignedJWT;
import eu.tealhelix.common.test.quarkus.InjectKeycloak;
import eu.tealhelix.common.test.quarkus.PostgresAndKeycloakTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * A user who signs in to the IDM on their own, with no retailer to hand them over, uses the compass with the access
 * token the IDM issued. The application has never seen such a user before their first request, and gives them a profile
 * then; the single-page application fires several requests at once as soon as the user is signed in, so the first
 * requests arrive together, and all of them must succeed on the one profile.
 * <p>
 * The user signs in with their password through the {@code howibuy} client, the one the single-page application uses.
 * That client signs users in through the browser only, so the test opens the password grant on it, in the test's own
 * Keycloak, to get a token without one. The resource arguments are those of {@code CompassWorkflowTest}, so both run
 * against the same Keycloak and the same application.
 */
@QuarkusTest
@WithTestResource(value = PostgresAndKeycloakTestResource.class, initArgs = {
		@ResourceArg(name = "pgContexts", value = "dev,appdata"),
		@ResourceArg(name = "pgConnectionDbUser", value = "th_howibuy"),
		@ResourceArg(name = "pgConnectionDbPassword", value = "th_howibuy"),
})
public class CompassIdmLoginTest {
	private static final String BASE = "/api/howibuy/v1/sfc";
	private static final String SPA_CLIENT = "howibuy";
	private static final String PASSWORD = "sandy-password";
	private static final int SIMULTANEOUS_FIRST_REQUESTS = 5;

	@InjectKeycloak
	private GenericContainer<?> keycloak;

	@Inject
	DataSource dataSource;

	@Test
	void aUserOfTheIdmTakesTheCompassWithTheirOwnAccessToken() throws Exception {
		var username = "sandy-" + UUID.randomUUID() + "@treedome.com";
		createIdmUser(username);
		var token = idmAccessToken(username);

		var firstRequests = IntStream.range(0, SIMULTANEOUS_FIRST_REQUESTS)
				.mapToObj(_ -> CompletableFuture.supplyAsync(() -> overviewStatus(token)))
				.toList();
		firstRequests.forEach(status -> assertEquals(200, status.join(), "every one of the first requests is served"));
		assertEquals(List.of(username), profileEmailsOf(SignedJWT.parse(token).getJWTClaimsSet().getSubject()),
				"one profile for the user, carrying the email the IDM knows them by");

		given().header("Authorization", "Bearer " + token)
				.contentType(JSON)
				.body("{\"option\":\"MODERATELY_IMPORTANT\"}")
				.when().put(BASE + "/questions/" + firstQuestionId(token) + "/answer")
				.then().statusCode(204);

		var answered = given().header("Authorization", "Bearer " + token)
				.when().get(BASE + "/overview")
				.then().statusCode(200).extract().jsonPath().getInt("overallProgress.answered");
		assertEquals(1, answered, "the answer is recorded against the user's profile");
	}

	private int overviewStatus(String token) {
		return given().header("Authorization", "Bearer " + token)
				.when().get(BASE + "/overview")
				.then().extract().statusCode();
	}

	private String firstQuestionId(String token) {
		List<Map<String, Object>> groups = given().header("Authorization", "Bearer " + token)
				.when().get(BASE + "/questions")
				.then().statusCode(200).extract().jsonPath().getList("$");
		@SuppressWarnings("unchecked")
		var questions = (List<Map<String, Object>>) groups.getFirst().get("questions");
		return (String) questions.getFirst().get("id");
	}

	private List<String> profileEmailsOf(String idmId) throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT email FROM TH_USER_PROFILE WHERE idm_id = ?")) {
			s.setString(1, idmId);
			try (var rs = s.executeQuery()) {
				var emails = new ArrayList<String>();
				while (rs.next()) {
					emails.add(rs.getString("email"));
				}
				return emails;
			}
		}
	}

	private void createIdmUser(String username) {
		var adminToken = adminToken();
		given().header("Authorization", "Bearer " + adminToken)
				.contentType(JSON)
				.body(Map.of(
						"username", username,
						"email", username,
						"emailVerified", true,
						"firstName", "Sandy",
						"lastName", "Cheeks",
						"enabled", true,
						"credentials", List.of(Map.of("type", "password", "value", PASSWORD, "temporary", false))))
				.when().post(keycloakUrl() + "/admin/realms/tealhelix/users")
				.then().statusCode(201);
		allowPasswordGrantOnSpaClient(adminToken);
	}

	private void allowPasswordGrantOnSpaClient(String adminToken) {
		var clientsUrl = keycloakUrl() + "/admin/realms/tealhelix/clients";
		List<Map<String, Object>> clients = given().header("Authorization", "Bearer " + adminToken)
				.queryParam("clientId", SPA_CLIENT)
				.when().get(clientsUrl)
				.then().statusCode(200).extract().jsonPath().getList("$");
		var client = new HashMap<>(clients.getFirst());
		client.put("directAccessGrantsEnabled", true);
		given().header("Authorization", "Bearer " + adminToken)
				.contentType(JSON)
				.body(client)
				.when().put(clientsUrl + "/" + client.get("id"))
				.then().statusCode(204);
	}

	private String adminToken() {
		return given()
				.formParam("grant_type", "password")
				.formParam("client_id", "admin-cli")
				.formParam("username", "admin")
				.formParam("password", "admin")
				.when().post(keycloakUrl() + "/realms/master/protocol/openid-connect/token")
				.then().statusCode(200).contentType(JSON).extract().path("access_token");
	}

	private String idmAccessToken(String username) {
		return given()
				.formParam("grant_type", "password")
				.formParam("client_id", SPA_CLIENT)
				.formParam("username", username)
				.formParam("password", PASSWORD)
				.formParam("scope", "openid")
				.when().post(keycloakUrl() + "/realms/tealhelix/protocol/openid-connect/token")
				.then().statusCode(200).contentType(JSON).extract().path("access_token");
	}

	private String keycloakUrl() {
		return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(KEYCLOAK_PORT);
	}
}
