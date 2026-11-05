package eu.tealhelix.howibuy.sys;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
				.body("paths.'/api/howibuy/v1/assessment/product/single'.post.responses.'200'.content.'application/json'.schema.'$ref'",
						equalTo("#/components/schemas/ProductAssessmentOutcome"));
	}

	/**
	 * An assessment that cannot identify the product says why, and its diagnostics say how far the identification got:
	 * the Retailer Product found by the key, its Classification and the archetype it names.
	 */
	@Test
	void describesTheFailureReasonAndTheIdentificationDiagnosticsOfTheOutcome() {
		given()
				.accept("application/json")
				.when().get("/api/howibuy/v1/openapi")
				.then()
				.statusCode(200)
				.body("components.schemas.ProductAssessmentOutcome.properties.failureReason.'$ref'",
						equalTo("#/components/schemas/IdentificationFailureReason"))
				.body("components.schemas.IdentificationFailureReason.enum",
						equalTo(List.of("UNKNOWN_PRODUCT", "NOT_CLASSIFIED", "NO_MATCHING_ARCHETYPE")))
				.body("components.schemas.ProductAssessmentOutcome.properties.diagnostics.'$ref'",
						equalTo("#/components/schemas/IdentificationDiagnostics"))
				.body("components.schemas.IdentificationDiagnostics.properties.retailerProductId.'$ref'",
						equalTo("#/components/schemas/RetailerProductId"))
				.body("components.schemas.RetailerProductId.type", equalTo("string"))
				.body("components.schemas.IdentificationDiagnostics.properties.classifier.'$ref'",
						equalTo("#/components/schemas/Classifier"))
				.body("components.schemas.IdentificationDiagnostics.properties.needsReview.type", equalTo("boolean"))
				.body("components.schemas.IdentificationDiagnostics.properties.pending.type", equalTo("boolean"))
				.body("components.schemas.IdentificationDiagnostics.properties.archetypeProductId.'$ref'",
						equalTo("#/components/schemas/ArchetypeProductId"))
				.body("components.schemas.IdentificationDiagnostics.properties.archetypeProductName.type", equalTo("string"));
	}

	/**
	 * An assessment request names the products by their keys and the language to answer in; the Catalogue they are
	 * looked up in comes from the token.
	 */
	@Test
	void describesTheAssessmentRequestsByProductKeysAndLanguage() {
		given()
				.accept("application/json")
				.when().get("/api/howibuy/v1/openapi")
				.then()
				.statusCode(200)
				.body("paths.'/api/howibuy/v1/assessment/product/single'.post.requestBody.content.'application/json'.schema.'$ref'",
						equalTo("#/components/schemas/SingleProductAssessmentRequest"))
				.body("components.schemas.SingleProductAssessmentRequest.properties.keySet()", equalTo(Set.of("productKey", "language")))
				.body("components.schemas.SingleProductAssessmentRequest.properties.productKey.type", equalTo("string"))
				.body("components.schemas.MultiProductAssessmentRequest.properties.keySet()", equalTo(Set.of("language", "productKeys")))
				.body("components.schemas.MultiProductAssessmentRequest.properties.productKeys.items.type", equalTo("string"))
				.body("components.schemas.ProductData", nullValue());
	}

	/**
	 * An assessment answers with its Recommendation, the scientific Overall Score of the Reference Product and the
	 * Alternatives, each naming the archetype product it stands for with the SAFAD taxonomy path that archetype sits in,
	 * its scientific Overall Score, and the product of the Catalogue that stands for it. All of it is what the front-end
	 * shows the user, so it belongs to the published contract rather than only to the Java type. This shape is the
	 * contract's version 0.3.0.
	 */
	@Test
	void describesTheRecommendationAndItsAlternativesWithTheirArchetypeScoreAndProduct() {
		given()
				.accept("application/json")
				.when().get("/api/howibuy/v1/openapi")
				.then()
				.statusCode(200)
				.body("info.version", equalTo("0.3.0"))
				.body("components.schemas.ProductAssessmentOutcome.properties.keySet()", equalTo(Set.of(
						"productKey", "type", "failureReason", "recommendation", "referenceOverallScore", "alternatives", "diagnostics")))
				.body("components.schemas.ProductAssessmentOutcome.properties.recommendation.'$ref'",
						equalTo("#/components/schemas/RecommendationType"))
				.body("components.schemas.RecommendationType.enum",
						equalTo(List.of("RECOMMENDED", "GOOD_ENOUGH", "NO_ALTERNATIVE", "NO_SCORE")))
				.body("components.schemas.ProductAssessmentOutcome.properties.referenceOverallScore.type", equalTo("number"))
				.body("components.schemas.ProductAssessmentOutcome.properties.alternatives.type", equalTo("array"))
				.body("components.schemas.ProductAssessmentOutcome.properties.alternatives.items.'$ref'",
						equalTo("#/components/schemas/AlternativeForProduct"))
				.body("components.schemas.AlternativeForProduct.properties.keySet()",
						equalTo(Set.of("archetype", "overallScore", "product")))
				.body("components.schemas.AlternativeForProduct.properties.archetype.'$ref'",
						equalTo("#/components/schemas/RecommendedArchetype"))
				.body("components.schemas.RecommendedArchetype.properties.id.'$ref'",
						equalTo("#/components/schemas/ArchetypeProductId"))
				.body("components.schemas.ArchetypeProductId.type", equalTo("string"))
				.body("components.schemas.ArchetypeProductId.properties", nullValue())
				.body("components.schemas.RecommendedArchetype.properties.name.type", equalTo("string"))
				.body("components.schemas.RecommendedArchetype.properties.l1Category.type", equalTo("string"))
				.body("components.schemas.RecommendedArchetype.properties.l2Category.type", equalTo("string"))
				.body("components.schemas.RecommendedArchetype.properties.l3Category.type", equalTo("string"))
				.body("components.schemas.AlternativeForProduct.properties.overallScore.type", equalTo("number"))
				.body("components.schemas.AlternativeForProduct.properties.product.'$ref'",
						equalTo("#/components/schemas/ListedProduct"))
				.body("components.schemas.ListedProduct.properties.productKey.'$ref'",
						equalTo("#/components/schemas/ProductKey"))
				.body("components.schemas.ListedProduct.properties.name.type", equalTo("string"))
				.body("components.schemas.ListedProduct.properties.language.type", equalTo("string"))
				.body("components.schemas.ListedProduct.properties.price.type", equalTo("number"))
				.body("components.schemas.ListedProduct.properties.currency.'$ref'",
						equalTo("#/components/schemas/Currency"))
				.body("components.schemas.AlternativeForProductType", nullValue());
	}

	/**
	 * A basket is assessed by the keys of its products and the language of the grade's label, the Catalogue coming from
	 * the token. The outcome says whether there was anything to assess, and grades the basket as a whole: no product is
	 * answered on its own.
	 */
	@Test
	void documentsTheBasketAssessment() {
		var document = openApiDocument();
		var assess = operation(document, "post", "/api/howibuy/v1/assessment/basket");

		assertEquals("#/components/schemas/BasketAssessmentRequest",
				at(assess, "requestBody", "content", "application/json", "schema", "$ref"),
				"the products are sent as the request body");
		assertEquals(Set.of("language", "productKeys"), ((Map<?, ?>) at(schemas(document), "BasketAssessmentRequest", "properties")).keySet());
		assertEquals("#/components/schemas/BasketAssessmentOutcome",
				at(assess, "responses", "200", "content", "application/json", "schema", "$ref"));
		var outcome = at(schemas(document), "BasketAssessmentOutcome", "properties");
		assertEquals(Set.of("type", "basketGrade", "basketGradeLabel", "intervention", "problematicProductKeys", "totalXp", "xpValue"), ((Map<?, ?>) outcome).keySet());
		assertEquals(List.of("ASSESSED", "NOTHING_TO_ASSESS"), at(schemas(document), "BasketAssessmentOutcomeType", "enum"));
		assertEquals(List.of("A", "B", "C"), at(schemas(document), "BasketGrade", "enum"));
		for (var status : List.of("400", "401", "403")) {
			assertNotNull(at(assess, "responses", status, "description"), "a refused basket is documented, status: " + status);
		}
	}

	/**
	 * The Retailer registers a purchase with its own token and gets the same outcome back. The purchase time is a
	 * date-time with an offset, and the description warns that every call is another purchase.
	 */
	@Test
	void documentsTheRegistrationOfAPurchasedBasket() {
		var document = openApiDocument();
		var purchase = operation(document, "post", "/api/howibuy/v1/assessment/basket/purchase");

		assertEquals("#/components/schemas/PurchasedBasketAssessmentRequest",
				at(purchase, "requestBody", "content", "application/json", "schema", "$ref"));
		var request = (Map<?, ?>) at(schemas(document), "PurchasedBasketAssessmentRequest", "properties");
		assertEquals(Set.of("correlationId", "catalogueId", "purchasedAt", "language", "productKeys"), request.keySet());
		assertEquals("#/components/schemas/OffsetDateTime", at(request, "purchasedAt", "$ref"));
		assertEquals("date-time", at(schemas(document), "OffsetDateTime", "format"));
		assertEquals("#/components/schemas/BasketAssessmentOutcome",
				at(purchase, "responses", "200", "content", "application/json", "schema", "$ref"));
		assertTrue(((String) purchase.get("description")).contains("exactly once per purchase"), "a repeated call is counted again");
		for (var status : List.of("400", "401", "403")) {
			assertNotNull(at(purchase, "responses", status, "description"), "a refused purchase is documented, status: " + status);
		}
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
				Set.of("Product assessment", "Basket assessment", "Session handoff", "Token exchange", "Catalogue changes", "Classification", "Sustainable Food Compass", "Sustainable Food Compass attempts", "Miscellaneous"),
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
	 * The Catalogue Sync reads the cursor and applies pages of changes on top of it. Applying answers 204 with no body,
	 * and a page that does not start at the cursor is refused with 409 and a message, which the sync has to handle by
	 * reading the cursor again.
	 */
	@Test
	void documentsTheCatalogueChanges() {
		var document = openApiDocument();
		var readCursor = operation(document, "get", "/api/howibuy/v1/catalogue-changes/cursor");
		var apply = operation(document, "post", "/api/howibuy/v1/catalogue-changes");

		assertEquals("#/components/schemas/CatalogueChangeCursorResponse",
				at(readCursor, "responses", "200", "content", "application/json", "schema", "$ref"),
				"the cursor answers the last sequence applied");
		assertEquals("#/components/schemas/CatalogueChangesRequest",
				at(apply, "requestBody", "content", "application/json", "schema", "$ref"),
				"a page is sent as the request body");
		assertNotNull(at(apply, "responses", "204", "description"), "applying documents its empty success outcome");
		assertNull(at(apply, "responses", "201"), "applying does not promise a Created it never sends");
		assertEquals("#/components/schemas/SingleMessageResponse",
				at(apply, "responses", "409", "content", "application/json", "schema", "$ref"),
				"a page not starting at the cursor answers a message");
		for (var operation : List.of(readCursor, apply)) {
			assertNotNull(at(operation, "responses", "403", "description"), "a caller other than the Catalogue Sync is documented");
		}
	}

	/**
	 * A retailer exchanges its token for one that acts as one of its users in one of its Catalogues, naming both in the
	 * request body. A request missing either is refused with a message, and a Catalogue that is not an active one of the
	 * retailer is refused like a retailer that may not act.
	 */
	@Test
	void documentsTheTokenExchange() {
		var document = openApiDocument();
		var exchange = operation(document, "post", "/api/howibuy/v1/tokenexchange");

		assertEquals("#/components/schemas/TokenExchangeRequest",
				at(exchange, "requestBody", "content", "application/json", "schema", "$ref"),
				"the user and the Catalogue are sent as the request body");
		var request = at(schemas(document), "TokenExchangeRequest", "properties");
		assertEquals("string", at(request, "correlationId", "type"));
		assertEquals("string", at(request, "catalogueId", "type"));
		assertEquals("#/components/schemas/TokenExchangeResponse",
				at(exchange, "responses", "200", "content", "application/json", "schema", "$ref"));
		assertEquals("#/components/schemas/SingleMessageResponse",
				at(exchange, "responses", "400", "content", "application/json", "schema", "$ref"),
				"a request missing the user or the Catalogue answers a message");
		assertNotNull(at(exchange, "responses", "403", "description"), "a Catalogue the retailer may not act in is documented");
	}

	/**
	 * The Catalogue Sync and operators read the pending products and record the attempts made outside this application.
	 * Recording answers 204 with no body, and an attempt made from an input hash that is no longer the product's is
	 * refused with 409 and a message, which the caller handles by reading the pending products again. They also read the
	 * Classifications to review, each attempt with the path its matching took, as an assessment carries it. Operators
	 * reopen the Classifications that came from a classifier version, and are answered how many.
	 */
	@Test
	void documentsTheClassification() {
		var document = openApiDocument();
		var findPending = operation(document, "get", "/api/howibuy/v1/classifications/pending");
		var recordAttempt = operation(document, "post", "/api/howibuy/v1/classification-attempts");
		var findToReview = operation(document, "get", "/api/howibuy/v1/classifications/review");
		var reopen = operation(document, "post", "/api/howibuy/v1/classifications/reopening");

		assertEquals("#/components/schemas/PendingProductDto",
				at(findPending, "responses", "200", "content", "application/json", "schema", "items", "$ref"),
				"the pending products are answered as a list");
		assertEquals("#/components/schemas/ClassificationToReviewDto",
				at(findToReview, "responses", "200", "content", "application/json", "schema", "items", "$ref"),
				"the Classifications to review are answered as a list");
		assertEquals("#/components/schemas/MatchDiagnostics",
				at(schemas(document), "ClassificationAttemptDto", "properties", "diagnostics", "$ref"),
				"an attempt carries the diagnostics of an assessment");
		assertEquals("#/components/schemas/ClassificationAttemptRequest",
				at(recordAttempt, "requestBody", "content", "application/json", "schema", "$ref"),
				"an attempt is sent as the request body");
		assertNotNull(at(recordAttempt, "responses", "204", "description"), "recording documents its empty success outcome");
		assertNull(at(recordAttempt, "responses", "201"), "recording does not promise a Created it never sends");
		for (var status : List.of("400", "404", "409")) {
			assertEquals("#/components/schemas/SingleMessageResponse",
					at(recordAttempt, "responses", status, "content", "application/json", "schema", "$ref"),
					"a refused attempt answers a message, status: " + status);
		}
		for (var operation : List.of(findPending, recordAttempt, findToReview)) {
			assertNotNull(at(operation, "responses", "403", "description"), "a caller other than the Catalogue Sync or an operator is documented");
		}
		assertEquals("#/components/schemas/ClassificationReopeningRequest",
				at(reopen, "requestBody", "content", "application/json", "schema", "$ref"),
				"the classifier version is sent as the request body");
		assertEquals("#/components/schemas/ClassificationReopeningResponse",
				at(reopen, "responses", "200", "content", "application/json", "schema", "$ref"));
		assertEquals("#/components/schemas/SingleMessageResponse",
				at(reopen, "responses", "400", "content", "application/json", "schema", "$ref"),
				"a refused reopening answers a message");
		assertNotNull(at(reopen, "responses", "403", "description"), "a caller other than an operator is documented");
	}

	/**
	 * The Catalogue Sync and operators have the LLM classify a product. The answer tells how it ended, and carries the
	 * attempt recorded and the Classification resolved, or neither when the reply of the LLM was malformed, with the
	 * path the matching took. A product unknown, not named in the language or changed meanwhile is refused with a
	 * message.
	 */
	@Test
	void documentsTheClassificationWithTheLlm() {
		var document = openApiDocument();
		var classify = operation(document, "post", "/api/howibuy/v1/llm-classifications");

		assertEquals("#/components/schemas/LlmClassificationRequest",
				at(classify, "requestBody", "content", "application/json", "schema", "$ref"),
				"the product and the language are sent as the request body");
		assertEquals("#/components/schemas/LlmClassificationResponse",
				at(classify, "responses", "200", "content", "application/json", "schema", "$ref"));
		var response = at(schemas(document), "LlmClassificationResponse", "properties");
		assertEquals("#/components/schemas/LlmClassificationOutcome", at(response, "outcome", "$ref"));
		assertEquals(List.of("MATCHED", "NO_MATCH", "MALFORMED_REPLY"), at(schemas(document), "LlmClassificationOutcome", "enum"));
		assertEquals("#/components/schemas/ClassificationAttemptDto", at(response, "attempt", "$ref"));
		assertEquals("#/components/schemas/ClassificationDto", at(response, "classification", "$ref"));
		assertEquals("#/components/schemas/MatchDiagnostics", at(response, "diagnostics", "$ref"));
		for (var status : List.of("400", "404", "409")) {
			assertEquals("#/components/schemas/SingleMessageResponse",
					at(classify, "responses", status, "content", "application/json", "schema", "$ref"),
					"a refused classification answers a message, status: " + status);
		}
		assertNotNull(at(classify, "responses", "403", "description"), "a caller other than the Catalogue Sync or an operator is documented");
	}

	@Test
	void documentsThePreviewOfTheClassificationWithTheLlm() {
		var document = openApiDocument();
		var preview = operation(document, "post", "/api/howibuy/v1/llm-classification-previews");

		assertEquals("#/components/schemas/LlmClassificationPreviewRequest",
				at(preview, "requestBody", "content", "application/json", "schema", "$ref"),
				"the product data is sent as the request body");
		assertEquals("#/components/schemas/LlmClassificationPreviewResponse",
				at(preview, "responses", "200", "content", "application/json", "schema", "$ref"));
		var response = at(schemas(document), "LlmClassificationPreviewResponse", "properties");
		assertEquals("#/components/schemas/LlmClassificationOutcome", at(response, "outcome", "$ref"), "the outcome is the classification's");
		assertEquals("#/components/schemas/MatchDiagnostics", at(response, "diagnostics", "$ref"));
		assertEquals("#/components/schemas/SingleMessageResponse",
				at(preview, "responses", "400", "content", "application/json", "schema", "$ref"),
				"refused product data answers a message");
		assertNotNull(at(preview, "responses", "403", "description"), "a caller other than an operator is documented");
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
