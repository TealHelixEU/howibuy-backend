package eu.tealhelix.howibuy.sys;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import eu.tealhelix.common.test.quarkus.PostgresTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * The application publishes a machine-readable OpenAPI document describing its HTTP contract, so consumers (e.g. the
 * single-page front-end) can build against a generated schema rather than a hand-written summary that drifts. The
 * document is derived from the JAX-RS resources at build time; this guards that it stays served and keeps describing
 * the Sustainable Food Compass read surface accurately.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, initArgs = @ResourceArg(name = "contexts", value = "appdata"))
public class OpenApiContractTest {
	@Test
	void publishesOpenApiDocumentDescribingTheCompassReadSurface() {
		given()
				.accept("application/json")
				.when().get("/q/openapi")
				.then()
				.statusCode(200)
				.body(containsString("sfc/overview"))
				.body("components.schemas.CompassOverviewDto", notNullValue());
	}

	/**
	 * Value-type ids ({@code CategoryId}, {@code QuestionId}) serialize as bare strings on the wire, but smallrye-openapi
	 * introspects their Java type and would otherwise emit a recursive object schema. The schema must match the wire.
	 */
	@Test
	void rendersValueTypeIdsAsStrings() {
		given()
				.accept("application/json")
				.when().get("/q/openapi")
				.then()
				.statusCode(200)
				.body("components.schemas.CategoryId.type", equalTo("string"))
				.body("components.schemas.CategoryId.properties", nullValue())
				.body("components.schemas.QuestionId.type", equalTo("string"))
				.body("components.schemas.QuestionId.properties", nullValue());
	}

	/**
	 * The Qute {@code /greeting} sample endpoint produces {@code text/html}, not part of the JSON API, and drags the
	 * entire Qute engine object graph into the document. It is excluded from the contract.
	 */
	@Test
	void omitsTheQuteSampleEndpointAndItsSchemaNoise() {
		given()
				.accept("application/json")
				.when().get("/q/openapi")
				.then()
				.statusCode(200)
				.body("paths.keySet()", not(hasItem("/api/howibuy/v1/greeting")))
				.body("components.schemas.TemplateInstance", nullValue())
				.body("components.schemas.EngineImpl", nullValue());
	}

	/**
	 * {@code ProductKey} (a {@code RepresentableAsString} value type) and the JDK value types {@code Locale} and
	 * {@code Currency} all serialize as bare strings on the wire — a language tag, a currency code — but smallrye-openapi
	 * would otherwise emit them as objects introspected from their Java shape. The schema must match the wire.
	 */
	@Test
	void rendersStringSerializedValueTypesAsStrings() {
		given()
				.accept("application/json")
				.when().get("/q/openapi")
				.then()
				.statusCode(200)
				.body("components.schemas.ProductKey.type", equalTo("string"))
				.body("components.schemas.ProductKey.properties", nullValue())
				.body("components.schemas.Locale.type", equalTo("string"))
				.body("components.schemas.Currency.type", equalTo("string"));
	}

	/**
	 * Each endpoint returns its response type directly, so smallrye-openapi describes the body from that type. This
	 * guards that the response envelopes are present and that the single-assessment endpoint's 200 body refers to the
	 * outcome schema.
	 */
	@Test
	void describesResponseBodySchemas() {
		given()
				.accept("application/json")
				.when().get("/q/openapi")
				.then()
				.statusCode(200)
				.body("components.schemas.HandoffResponse", notNullValue())
				.body("components.schemas.SessionTokenResponse", notNullValue())
				.body("components.schemas.TokenExchangeResponse", notNullValue())
				.body("components.schemas.MultiProductAssessmentResponse", notNullValue())
				.body("paths.'/api/howibuy/v1/assessment/single'.post.responses.'200'.content.'application/json'.schema.'$ref'",
						equalTo("#/components/schemas/ProductAssessmentOutcome"));
	}

	/**
	 * An assessment answers with three alternatives, each naming the archetype product it recommends and the two
	 * overall scores that put it there. The scores are what the front-end shows the user, so they belong to the
	 * published contract rather than only to the Java type.
	 */
	@Test
	void describesTheRecommendedAlternativeWithItsArchetypeAndScores() {
		given()
				.accept("application/json")
				.when().get("/q/openapi")
				.then()
				.statusCode(200)
				.body("components.schemas.AlternativeForProduct.properties.archetypeProductId.'$ref'",
						equalTo("#/components/schemas/UUID"))
				.body("components.schemas.UUID.type", equalTo("string"))
				.body("components.schemas.UUID.format", equalTo("uuid"))
				.body("components.schemas.AlternativeForProduct.properties.referenceOverallScore.type", equalTo("number"))
				.body("components.schemas.AlternativeForProduct.properties.alternativeOverallScore.type", equalTo("number"))
				.body("components.schemas.AlternativeForProduct.properties.type.'$ref'",
						equalTo("#/components/schemas/AlternativeForProductType"))
				.body("components.schemas.AlternativeForProductType.enum",
						hasItems("SUGGESTION", "GOOD_ENOUGH", "NO_SUGGESTION"));
	}
}
