package eu.tealhelix.sfc.tests;

import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

/**
 * HTTP-level access control for the compass endpoints: every one requires an authenticated end-user. An anonymous
 * request — read or write — is answered with 401 and a {@code Bearer} challenge (via the shared
 * {@code NotAuthenticatedException} mapper).
 */
@QuarkusTest
@WithCompassDb
public class CompassAccessControlTest {

	@Test
	void rejectsAnUnauthenticatedReadWith401AndBearerChallenge() {
		RestAssured.given()
				.when()
				.get("/api/howibuy/v1/sfc/categories")
				.then()
				.statusCode(401)
				.header("WWW-Authenticate", equalTo("Bearer"));
	}

	@Test
	void rejectsAnUnauthenticatedAnswerWith401AndBearerChallenge() {
		RestAssured.given()
				.when()
				.put("/api/howibuy/v1/sfc/questions/11111111-1111-1111-1111-111111111111/answer")
				.then()
				.statusCode(401)
				.header("WWW-Authenticate", equalTo("Bearer"));
	}
}
