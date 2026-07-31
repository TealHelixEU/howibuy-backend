package eu.tealhelix.howibuy.tests;

import static eu.tealhelix.common.test.quarkus.PostgresAndKeycloakTestResource.KEYCLOAK_PORT;
import static io.restassured.http.ContentType.JSON;

import io.restassured.RestAssured;
import org.testcontainers.containers.GenericContainer;

/**
 * The access token the IDM issues to a retailer's service account, obtained the way a retailer's own backend obtains it
 * before calling this application. The credentials are those of the {@code lime_fresh} client of the test realm, see
 * {@code keycloak-auth-service.sh}.
 */
public interface RetailerServiceAccount {
	String CLIENT_ID = "lime_fresh";
	String CLIENT_SECRET = "GrZ4Vd8xWAthuLFOXe1tlYvAtXo8INv1";

	static String accessToken(GenericContainer<?> keycloak) {
		return RestAssured
				.given()
				.formParam("grant_type", "client_credentials")
				.formParam("client_id", CLIENT_ID)
				.formParam("client_secret", CLIENT_SECRET)
				.when()
				.post("http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(KEYCLOAK_PORT) + "/realms/tealhelix/protocol/openid-connect/token")
				.then()
				.statusCode(200)
				.contentType(JSON)
				.extract()
				.path("access_token");
	}
}
