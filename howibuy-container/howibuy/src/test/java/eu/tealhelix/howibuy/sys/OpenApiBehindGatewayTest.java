package eu.tealhelix.howibuy.sys;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import eu.tealhelix.common.test.quarkus.PostgresTestResource;
import eu.tealhelix.howibuy.JaxRsAppHowiBuyV1;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

/**
 * In production the application sits behind a gateway that rewrites {@code /howibuy/api/v1/*} onto the application's
 * own {@code /api/howibuy/v1/*}, so the published document has to describe the URL a caller outside the gateway dials.
 * It does so by leaving the JAX-RS application path out of the path keys and naming the public base path as the only
 * server. Neither setting is made for a local build — they are made for the container image and its environment, see
 * the {@code Dockerfile} — so this is the only cover the published shape of that document has.
 */
@QuarkusTest
@TestProfile(OpenApiBehindGatewayTest.BehindGateway.class)
@WithTestResource(value = PostgresTestResource.class, initArgs = @ResourceArg(name = "contexts", value = "appdata"))
public class OpenApiBehindGatewayTest {
	private static final String PUBLIC_BASE_PATH = "/howibuy/api/v1";

	/**
	 * The pair the container build and its environment carry. Dropping the application path is fixed at augmentation
	 * time, naming the server is not, but a document with one and not the other describes nothing that answers.
	 */
	public static class BehindGateway implements QuarkusTestProfile {
		@Override
		public Map<String, String> getConfigOverrides() {
			return Map.of(
					"mp.openapi.extensions.smallrye.application-path.disable", "true",
					"quarkus.smallrye-openapi.servers", PUBLIC_BASE_PATH);
		}
	}

	@Test
	void namesThePublicBasePathAsTheOnlyServer() {
		assertEquals(List.of(PUBLIC_BASE_PATH), openApiDocument().getList("servers.url"),
				"a caller outside the gateway is handed one base path, not the container's own host");
	}

	/**
	 * A path key that still carried the application path would be appended to the base path above, dialling a URL the
	 * gateway does not answer.
	 */
	@Test
	void leavesTheApplicationPathOutOfTheOperationPaths() {
		Map<String, Object> paths = openApiDocument().getMap("paths");
		assertEquals(List.of(), paths.keySet().stream().filter(p -> p.startsWith(JaxRsAppHowiBuyV1.APPLICATION_PATH)).sorted().toList(),
				"no path repeats the prefix the gateway has already rewritten");
		assertTrue(paths.containsKey("/sfc/categories"), "the operations are described at the path the gateway forwards");
	}

	private static JsonPath openApiDocument() {
		return given()
				.accept("application/json")
				.when().get("/api/howibuy/v1/openapi")
				.then()
				.statusCode(200)
				.extract().jsonPath();
	}
}
