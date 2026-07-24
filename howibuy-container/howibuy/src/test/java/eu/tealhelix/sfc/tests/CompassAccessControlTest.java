package eu.tealhelix.sfc.tests;

import static org.hamcrest.Matchers.equalTo;

import eu.tealhelix.common.test.quarkus.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

/**
 * HTTP-level access control for the compass reads: every endpoint requires an authenticated end-user. An anonymous
 * request is answered with 401 and a {@code Bearer} challenge (via the shared {@code NotAuthenticatedException} mapper).
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresTestResource.class, initArgs = {
		@ResourceArg(name = "contexts", value = "appdata")
})
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
}
