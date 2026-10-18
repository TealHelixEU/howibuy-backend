package eu.tealhelix.catalogue.tests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import eu.tealhelix.catalogue.test.quarkus.WithCatalogueDb;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

/**
 * The version endpoint tells which build a client is talking to, without a token, and is part of the published contract.
 */
@QuarkusTest
@WithCatalogueDb
class VersionResourceTest {
	@Test
	void reportsTheVersionAndTheCommitOfTheBuild() {
		JsonPath versionInfo = given()
				.accept("application/json")
				.when().get("/api/catalogue/v1/version")
				.then()
				.statusCode(200)
				.extract().jsonPath();

		assertThat(versionInfo.getString("version")).as("the project version, filled in by the build").matches("\\d+\\.\\d+\\.\\d+.*");
		assertThat(versionInfo.getString("gitHash")).as("the abbreviated commit id, filled in by the build").matches("[0-9a-f]{7,}");
	}

	@Test
	void isDescribedInTheOpenApiDocument() {
		JsonPath openApiDocument = given()
				.accept("application/json")
				.when().get("/api/catalogue/v1/openapi")
				.then()
				.statusCode(200)
				.extract().jsonPath();

		assertThat(openApiDocument.getMap("paths")).containsKey("/api/catalogue/v1/version");
	}
}
