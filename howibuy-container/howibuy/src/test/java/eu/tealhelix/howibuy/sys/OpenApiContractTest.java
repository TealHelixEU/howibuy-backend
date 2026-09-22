package eu.tealhelix.howibuy.sys;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import eu.tealhelix.common.test.quarkus.PostgresTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
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
	private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options");
	private static final Set<String> GENERATED_DESCRIPTIONS = Set.of("OK", "Created", "No Content", "Bad Request", "Not Found");

	@Test
	void publishesOpenApiDocumentDescribingTheCompassReadSurface() {
		given()
				.accept("application/json")
				.when().get("/api/howibuy/v1/openapi")
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
				.when().get("/api/howibuy/v1/openapi")
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
				.when().get("/api/howibuy/v1/openapi")
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
				.when().get("/api/howibuy/v1/openapi")
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
				.when().get("/api/howibuy/v1/openapi")
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
	 * An assessment answers with three alternatives, each naming the archetype product it recommends, the SAFAD
	 * taxonomy path that archetype sits in and the two overall scores that put it there. All of it is what the
	 * front-end shows the user, so it belongs to the published contract rather than only to the Java type.
	 */
	@Test
	void describesTheRecommendedAlternativeWithItsArchetypeCategoriesAndScores() {
		given()
				.accept("application/json")
				.when().get("/api/howibuy/v1/openapi")
				.then()
				.statusCode(200)
				.body("components.schemas.AlternativeForProduct.properties.archetypeProductId.'$ref'",
						equalTo("#/components/schemas/ArchetypeProductId"))
				.body("components.schemas.ArchetypeProductId.type", equalTo("string"))
				.body("components.schemas.ArchetypeProductId.properties", nullValue())
				.body("components.schemas.AlternativeForProduct.properties.l1Category.type", equalTo("string"))
				.body("components.schemas.AlternativeForProduct.properties.l2Category.type", equalTo("string"))
				.body("components.schemas.AlternativeForProduct.properties.l3Category.type", equalTo("string"))
				.body("components.schemas.AlternativeForProduct.properties.referenceOverallScore.type", equalTo("number"))
				.body("components.schemas.AlternativeForProduct.properties.alternativeOverallScore.type", equalTo("number"))
				.body("components.schemas.AlternativeForProduct.properties.type.'$ref'",
						equalTo("#/components/schemas/AlternativeForProductType"))
				.body("components.schemas.AlternativeForProductType.enum",
						hasItems("SUGGESTION", "GOOD_ENOUGH", "NO_SUGGESTION"));
	}

	/**
	 * Left to itself the generator names every operation after the Java method behind it ("Assess Single Product") and
	 * leaves it at that, which tells a consumer no more than the URL already did. Every operation carries a summary and
	 * a description written for the consumer, and so does every path and query parameter it takes.
	 */
	@Test
	void documentsEveryOperationAndItsParameters() {
		var undocumented = new ArrayList<String>();
		operations(openApiDocument()).forEach((name, operation) -> {
			if (isBlank(operation.get("summary"))) undocumented.add(name + ": summary");
			if (isBlank(operation.get("description"))) undocumented.add(name + ": description");
			for (var parameter : parameters(operation)) {
				if (isBlank(parameter.get("description"))) undocumented.add(name + ": parameter " + parameter.get("name"));
			}
		});
		assertEquals(List.of(), undocumented, "every operation and parameter of the published contract is documented");
	}

	/**
	 * An outcome the generator inferred describes itself with the reason phrase of its status ("Bad Request"), which
	 * tells a consumer nothing about what this application refused and why. Every outcome of every operation says what
	 * it means in this API.
	 */
	@Test
	void documentsWhatEveryOutcomeMeans() {
		var unexplained = new ArrayList<String>();
		operations(openApiDocument()).forEach((name, operation) -> responses(operation).forEach((status, response) -> {
			var description = String.valueOf(response.get("description"));
			if (isBlank(description) || GENERATED_DESCRIPTIONS.contains(description)) unexplained.add(name + " " + status + ": " + description);
		}));
		assertEquals(List.of(), unexplained, "every outcome says what it means, rather than repeating its status");
	}

	/**
	 * Swagger UI groups the operations by tag, so the tags are the first thing a consumer reads. Generated ones are the
	 * resource class names ("Compass Attempt Resource"); these are named after the parts of the API, and each says what
	 * it groups.
	 */
	@Test
	void groupsTheOperationsUnderDescribedTags() {
		var document = openApiDocument();
		List<Map<String, Object>> tags = document.get("tags");
		assertNotNull(tags, "the document declares the tags it groups operations under");
		var undescribed = tags.stream().filter(tag -> isBlank(tag.get("description"))).map(tag -> String.valueOf(tag.get("name"))).sorted().toList();
		assertEquals(List.of(), undescribed, "every tag says what it groups");

		var declared = tags.stream().map(tag -> String.valueOf(tag.get("name"))).collect(Collectors.toSet());
		assertEquals(
				Set.of("Product assessment", "Session handoff", "Token exchange", "Sustainable Food Compass", "Sustainable Food Compass attempts", "Miscellaneous"),
				declared,
				"the API is grouped by what its parts do");
		var ungrouped = new ArrayList<String>();
		operations(document).forEach((name, operation) -> {
			var operationTags = tagsOf(operation);
			if (!declared.containsAll(operationTags)) ungrouped.add(name + ": " + operationTags);
		});
		assertEquals(List.of(), ungrouped, "every operation sits under a declared tag");
	}

	/**
	 * The compass writes answer 204 with no body. Left to itself the generator assumes 201 for a {@code POST}, so the
	 * document promised a "Created" the application never sends; each write states the outcome it really has.
	 */
	@Test
	void documentsTheEmptyOutcomeOfTheCompassWrites() {
		var document = openApiDocument();
		var writes = List.of(
				operation(document, "post", "/api/howibuy/v1/sfc/attempts"),
				operation(document, "post", "/api/howibuy/v1/sfc/attempts/current/completion"),
				operation(document, "put", "/api/howibuy/v1/sfc/questions/{questionId}/answer"));
		for (var write : writes) {
			assertNotNull(at(write, "responses", "204", "description"), "the write documents its empty success outcome");
			assertNull(at(write, "responses", "201"), "the write does not promise a Created it never sends");
		}
	}

	/**
	 * The compass writes are refused in ways a client has to handle: a start that clashes with the state of the user's
	 * attempts (409 — one already in progress, or a completed one still inside its stability window, which answers the
	 * moment it ends), and a completion with questions still unanswered (422, naming exactly those questions). Each
	 * refusal is in the document with the body it actually returns.
	 */
	@Test
	void documentsHowTheCompassWritesAreRefused() {
		var document = openApiDocument();
		var startAttempt = operation(document, "post", "/api/howibuy/v1/sfc/attempts");
		var complete = operation(document, "post", "/api/howibuy/v1/sfc/attempts/current/completion");

		assertEquals(
				List.of("#/components/schemas/StabilityWindowResponse", "#/components/schemas/SingleMessageResponse"),
				refsOf(at(startAttempt, "responses", "409", "content", "application/json", "schema", "anyOf")),
				"a refused start names both bodies it can answer with");
		assertEquals("#/components/schemas/SingleMessageResponse",
				at(complete, "responses", "409", "content", "application/json", "schema", "$ref"),
				"completing with nothing in progress answers a message");
		assertEquals("#/components/schemas/IncompleteAttemptResponse",
				at(complete, "responses", "422", "content", "application/json", "schema", "$ref"),
				"an incomplete completion answers the unanswered question ids");
		assertNotNull(at(startAttempt, "responses", "401", "description"), "an unauthenticated caller is documented");
		assertNotNull(at(startAttempt, "responses", "403", "description"), "a service account calling an end-user operation is documented");
	}

	/**
	 * Every operation but two is reached with a bearer token, so the scheme is declared once and each operation states
	 * that it needs it — which is also what puts the Authorize button in Swagger UI. Redeeming a handoff ticket carries
	 * no token, the ticket in the body being the credential, and the version is public; both say so by naming no scheme.
	 */
	@Test
	void declaresTheBearerTokenAndTheOperationsThatCarryNone() {
		var document = openApiDocument();
		assertEquals("http", document.get("components.securitySchemes.bearerAuth.type"));
		assertEquals("bearer", document.get("components.securitySchemes.bearerAuth.scheme"));
		assertEquals("JWT", document.get("components.securitySchemes.bearerAuth.bearerFormat"));
		assertFalse(isBlank(document.get("components.securitySchemes.bearerAuth.description")), "the scheme says which tokens it means");

		var tokenless = new ArrayList<String>();
		operations(document).forEach((name, operation) -> {
			if (!requiresTheBearerToken(operation)) tokenless.add(name);
		});
		assertEquals(
				List.of("GET /api/howibuy/v1/version", "POST /api/howibuy/v1/handoff/redeem"),
				tokenless.stream().sorted().toList(),
				"only the ticket redemption and the version are reachable without a token");
	}

	/**
	 * The schemas are the other half of what a consumer reads. Every type this application puts on the wire describes
	 * itself, wherever that text is kept: the DTOs carry it as annotations, while the framework-free model types are
	 * described from {@code META-INF/openapi.yaml}, which is merged into the scanned document. Those modules take no
	 * framework dependency — the same line {@link StringValueTypeSchemaFilter} exists to avoid crossing.
	 */
	@Test
	void describesTheTypesDefinedForTheWire() {
		var undescribed = schemas(openApiDocument()).entrySet().stream()
				.filter(schema -> isBlank(schema.getValue().get("description")))
				.map(Map.Entry::getKey)
				.sorted()
				.toList();
		assertEquals(List.of(), undescribed, "every type defined for the wire describes itself");
	}

	/**
	 * A type that describes itself but leaves its fields bare tells a consumer half of what it needs: the fields are
	 * where the units, the ranges and the conditions under which something is absent are stated.
	 */
	@Test
	void describesTheFieldsOfThoseTypes() {
		var undescribed = new ArrayList<String>();
		schemas(openApiDocument()).forEach((name, schema) -> fields(schema).forEach((field, definition) -> {
			if (isBlank(definition.get("description"))) undescribed.add(name + "." + field);
		}));
		assertEquals(List.of(), undescribed.stream().sorted().toList(), "every field of every type says what it carries");
	}

	/**
	 * The text for the model types is attached by name from outside the code, so a rename or a typo leaves it hanging
	 * on a type or a field that does not exist — silently, since nothing in the build resolves those names against the
	 * model. Such a leftover is recognisable: it carries the text and nothing else, where a scanned one always says
	 * what it holds.
	 */
	@Test
	void attachesThatTextOnlyToTypesAndFieldsThatExist() {
		var stray = new ArrayList<String>();
		schemas(openApiDocument()).forEach((name, schema) -> {
			if (schema.get("type") == null) stray.add(name);
			fields(schema).forEach((field, definition) -> {
				if (definition.get("type") == null && definition.get("$ref") == null) stray.add(name + "." + field);
			});
		});
		assertEquals(List.of(), stray.stream().sorted().toList(), "no description hangs on a name the scan never produced");
	}

	private static JsonPath openApiDocument() {
		return given()
				.accept("application/json")
				.when().get("/api/howibuy/v1/openapi")
				.then()
				.statusCode(200)
				.extract().jsonPath();
	}

	/**
	 * Every operation of the document, keyed by the {@code "METHOD /path"} a reader of the document recognises it by.
	 */
	private static Map<String, Map<String, Object>> operations(JsonPath document) {
		var operations = new LinkedHashMap<String, Map<String, Object>>();
		paths(document).forEach((path, pathItem) -> pathItem.forEach((method, operation) -> {
			if (HTTP_METHODS.contains(method)) operations.put(method.toUpperCase(Locale.ROOT) + " " + path, operation);
		}));
		return operations;
	}

	private static Map<String, Object> operation(JsonPath document, String method, String path) {
		var pathItem = paths(document).get(path);
		assertNotNull(pathItem, "the document describes " + path);
		var operation = pathItem.get(method);
		assertNotNull(operation, "the document describes " + method.toUpperCase(Locale.ROOT) + " " + path);
		return operation;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Map<String, Map<String, Object>>> paths(JsonPath document) {
		return document.get("paths");
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> parameters(Map<String, Object> operation) {
		return (List<Map<String, Object>>) operation.getOrDefault("parameters", List.of());
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Map<String, Object>> responses(Map<String, Object> operation) {
		return (Map<String, Map<String, Object>>) operation.getOrDefault("responses", Map.of());
	}

	@SuppressWarnings("unchecked")
	private static List<String> tagsOf(Map<String, Object> operation) {
		return (List<String>) operation.getOrDefault("tags", List.of());
	}

	@SuppressWarnings("unchecked")
	private static boolean requiresTheBearerToken(Map<String, Object> operation) {
		var requirements = (List<Map<String, Object>>) operation.getOrDefault("security", List.of());
		return requirements.stream().anyMatch(requirement -> requirement.containsKey(BEARER_AUTH));
	}

	@SuppressWarnings("unchecked")
	private static List<String> refsOf(Object alternatives) {
		if (alternatives == null) return List.of();
		else return ((List<Map<String, String>>) alternatives).stream().map(alternative -> alternative.get("$ref")).toList();
	}

	/**
	 * The value at a chain of keys, or {@code null} as soon as a step of it is missing.
	 */
	private static Object at(Object node, String... keys) {
		var current = node;
		for (var key : keys) {
			if (!(current instanceof Map<?, ?> map)) return null;
			current = map.get(key);
		}
		return current;
	}

	private static Map<String, Map<String, Object>> schemas(JsonPath document) {
		return document.get("components.schemas");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Map<String, Object>> fields(Map<String, Object> schema) {
		return (Map<String, Map<String, Object>>) schema.getOrDefault("properties", Map.of());
	}

	private static boolean isBlank(Object text) {
		return text == null || text.toString().isBlank();
	}
}
