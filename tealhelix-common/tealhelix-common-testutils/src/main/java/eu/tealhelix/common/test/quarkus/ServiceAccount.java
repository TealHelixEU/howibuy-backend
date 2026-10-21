package eu.tealhelix.common.test.quarkus;

import static eu.tealhelix.common.test.quarkus.PostgresAndKeycloakTestResource.KEYCLOAK_PORT;
import static io.restassured.http.ContentType.JSON;

import io.restassured.RestAssured;
import org.testcontainers.containers.GenericContainer;

/**
 * The service accounts of the test realm, whose access tokens are obtained the way a client's own backend obtains them
 * before calling the services of TealHelix. The credentials are those of the clients of the test realm, see
 * {@code keycloak-auth-service.sh}.
 */
public enum ServiceAccount {
	/** The {@code lime_fresh} client, a retailer. */
	RETAILER("lime_fresh", "GrZ4Vd8xWAthuLFOXe1tlYvAtXo8INv1"),

	/** The {@code catalogue-sync} client, which keeps the catalogues current and is no retailer. */
	CATALOGUE_SYNC("catalogue-sync", "7poZpd5n2Abtu7LKcB0MoAWLHBUnhQzU");

	private final String clientId;
	private final String clientSecret;

	ServiceAccount(String clientId, String clientSecret) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	/**
	 * @return The secret of the client, for a client that obtains its access tokens itself
	 */
	public String clientSecret() {
		return clientSecret;
	}

	public String accessToken(GenericContainer<?> keycloak) {
		return RestAssured
				.given()
				.formParam("grant_type", "client_credentials")
				.formParam("client_id", clientId)
				.formParam("client_secret", clientSecret)
				.when()
				.post("http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(KEYCLOAK_PORT) + "/realms/tealhelix/protocol/openid-connect/token")
				.then()
				.statusCode(200)
				.contentType(JSON)
				.extract()
				.path("access_token");
	}
}
