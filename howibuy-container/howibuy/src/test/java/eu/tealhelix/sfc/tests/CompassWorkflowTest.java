package eu.tealhelix.sfc.tests;

import static eu.tealhelix.common.test.quarkus.PostgresAndKeycloakTestResource.KEYCLOAK_PORT;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import eu.tealhelix.common.test.quarkus.InjectKeycloak;
import eu.tealhelix.common.test.quarkus.PostgresAndKeycloakTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * The one Keycloak-backed end-to-end test: a real user, authenticated with a real Keycloak-issued token, takes the
 * compass through the public API along the happy path — read the (empty) overview, walk a category to the end via
 * next-question answering each frontier, answer the rest, complete, and read the overview back to see the attempt locked
 * and fully answered. Everything runs for real: the JWT filter, authorization, resources, services, DAOs, the database
 * and JSON mapping. It is deliberately the only test that pays Keycloak's slow start-up; every finer case lives at the
 * faster Postgres-only and unit seams ({@code CompassOverviewTest}, {@code CompassCompletionTest},
 * {@code CompassNavigationTest}, {@code CompassReadServiceImplTest}).
 * <p>
 * The user is minted the way {@code CorrelationIdWorkflowTest} does it: a {@code lime_fresh} service token exchanged at
 * {@code /tokenexchange} for an impersonation token, which for a brand-new correlation id auto-creates the backing
 * {@code TH_USER_PROFILE} row (so the attempt's user FK is satisfied) and is not a service account.
 */
@QuarkusTest
@WithTestResource(value = PostgresAndKeycloakTestResource.class, initArgs = {
		@ResourceArg(name = "pgContexts", value = "dev,appdata"),
		@ResourceArg(name = "pgConnectionDbUser", value = "th_howibuy"),
		@ResourceArg(name = "pgConnectionDbPassword", value = "th_howibuy"),
})
public class CompassWorkflowTest {
	private static final String ROOT = "/api/howibuy/v1";
	private static final String BASE = ROOT + "/sfc";

	@InjectKeycloak
	private GenericContainer<?> keycloak;

	@Test
	void aUserTakesTheCompassEndToEndThroughTheRealApi() {
		var token = endUserToken();

		var fresh = overview(token);
		assertNull(fresh.getString("attemptStatus"), "no attempt yet");
		assertTrue(fresh.getBoolean("eligibleToStartNewAttempt"), "a new user may start");
		assertEquals(0, fresh.getInt("overallProgress.answered"));
		assertEquals(43, fresh.getInt("overallProgress.total"), "the whole item pool is visible before starting");

		var economicId = categoryIdOf(token, "ECONOMIC");
		walkToCompletionViaNextQuestion(token, economicId);
		answerEveryQuestionOutside(token, economicId);

		given().header("Authorization", "Bearer " + token)
				.when().post(BASE + "/attempts/current/completion")
				.then().statusCode(204);

		var locked = overview(token);
		assertEquals("COMPLETED", locked.getString("attemptStatus"), "the attempt is now a completed record");
		assertEquals(43, locked.getInt("overallProgress.answered"));
		assertEquals(100, locked.getInt("overallProgress.percentage"), "every question answered");
		assertFalse(locked.getBoolean("eligibleToStartNewAttempt"), "inside the freshly started stability window");
		assertNotNull(locked.getString("eligibleAt"), "the user is told when a re-take becomes possible");
	}

	private void walkToCompletionViaNextQuestion(String token, String categoryId) {
		var complete = false;
		for (var step = 0; step < 100 && !complete; step++) {
			var next = given().header("Authorization", "Bearer " + token)
					.queryParam("lang", "en")
					.when().get(BASE + "/categories/" + categoryId + "/next-question")
					.then().statusCode(200).extract().jsonPath();
			complete = next.getBoolean("complete");
			if (!complete) {
				answer(token, next.getString("question.id"));
			}
		}
		assertTrue(complete, "the category was walked to the end via next-question");
	}

	private void answerEveryQuestionOutside(String token, String walkedCategoryId) {
		List<Map<String, Object>> groups = given().header("Authorization", "Bearer " + token)
				.queryParam("lang", "en")
				.when().get(BASE + "/questions")
				.then().statusCode(200).extract().jsonPath().getList("$");
		for (var group : groups) {
			if (walkedCategoryId.equals(group.get("categoryId"))) {
				continue;
			}
			@SuppressWarnings("unchecked")
			var questions = (List<Map<String, Object>>) group.get("questions");
			questions.forEach(question -> answer(token, (String) question.get("id")));
		}
	}

	private void answer(String token, String questionId) {
		given().header("Authorization", "Bearer " + token)
				.contentType(JSON)
				.body("{\"option\":\"MODERATELY_IMPORTANT\"}")
				.when().put(BASE + "/questions/" + questionId + "/answer")
				.then().statusCode(204);
	}

	private JsonPath overview(String token) {
		return given().header("Authorization", "Bearer " + token)
				.queryParam("lang", "en")
				.when().get(BASE + "/overview")
				.then().statusCode(200).contentType(JSON).extract().jsonPath();
	}

	private String categoryIdOf(String token, String dimension) {
		List<Map<String, Object>> categories = given().header("Authorization", "Bearer " + token)
				.queryParam("lang", "en")
				.when().get(BASE + "/categories")
				.then().statusCode(200).extract().jsonPath().getList("$");
		return categories.stream()
				.filter(category -> dimension.equals(category.get("dimension")))
				.map(category -> (String) category.get("id"))
				.findFirst().orElseThrow();
	}

	/** A real impersonation token for a fresh end-user, via the {@code lime_fresh} service account and /tokenexchange. */
	private String endUserToken() {
		var serviceToken = given()
				.formParam("grant_type", "client_credentials")
				.formParam("client_id", "lime_fresh")
				.formParam("client_secret", "GrZ4Vd8xWAthuLFOXe1tlYvAtXo8INv1")
				.when().post("http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(KEYCLOAK_PORT)
						+ "/realms/tealhelix/protocol/openid-connect/token")
				.then().statusCode(200).contentType(JSON).extract().path("access_token");

		return given()
				.header("Authorization", "Bearer " + serviceToken)
				.header("Content-Type", "application/json")
				.body("{\"correlationId\": \"SFC-WORKFLOW\"}")
				.when().post(ROOT + "/tokenexchange")
				.then().statusCode(200).contentType(JSON).extract().path("access_token");
	}
}
