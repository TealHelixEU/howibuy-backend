package eu.tealhelix.howibuy.tests;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.common.test.quarkus.PostgresTestResource;
import eu.tealhelix.common.v1.model.Role;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.jaxrs.authentication.jwt.TokenHelper;
import eu.tealhelix.howibuy.jaxrs.authentication.jwt.TokenHelperImpl;
import eu.tealhelix.howibuy.services.model.FoodTerm;
import eu.tealhelix.howibuy.services.v1.ai.AiSelection;
import eu.tealhelix.howibuy.services.v1.ai.ProductAssessmentAiFacade;
import eu.tealhelix.howibuy.services.v1.ai.impl.ProductAssessmentAiFacadeImpl;
import eu.tealhelix.howibuy.services.v1.impl.ArchetypeMatching;
import eu.tealhelix.howibuy.v1.model.ProductBaseData;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Finding the pending products, recording Classification Attempts, classifying with the LLM and finding the
 * Classifications to review against a real database, with the products brought in through the Catalogue Changes as the
 * Catalogue Sync brings them. The stored Classification is asserted over JDBC. JWT decoding is faked (a stubbed
 * {@link TokenHelper}), and so is the language model (a stubbed {@link ProductAssessmentAiFacade}), whose answers cannot
 * be told in advance.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, initArgs = @ResourceArg(name = "contexts", value = "appdata"))
public class ClassificationsTest {
	private static final String BASE = "/api/howibuy/v1";
	private static final String ISO_DATE_TIME = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*";

	private static final String RETAILER_ID = "6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11";
	private static final String CATALOGUE_GR = "test-gr";

	private static final User CATALOGUE_SYNC = new UserImpl(new UserIdImpl("1d0c6b0e-3b5f-4d2a-8f7e-2a9c4b6d8e33"), "catalogue-sync", null, false, false, Set.of(Role.CATALOGUE_SYNC));
	private static final User RETAILER = new UserImpl(new UserIdImpl(RETAILER_ID), "test-retailer", null, false, false, Set.of(Role.RETAILER));
	private static final User OPERATOR = new UserImpl(new UserIdImpl("4e2d8c1a-7b3f-4a6e-9c5d-3f1b7a9e2c44"), "operator", null, false, true, Set.of(Role.OPERATOR));
	/**
	 * The diagnostics of a matching stopped at the first level, as it is for a product whose name the glossary does not
	 * know, so the LLM is asked from the first level on.
	 */
	private static final Map<String, Object> NO_LEVEL_REACHED = new HashMap<>();

	static {
		for (var level : List.of("l1Category", "l2Category", "l3Category", "archetypeProduct")) NO_LEVEL_REACHED.put(level, null);
	}

	private static final Map<String, User> USERS_BY_TOKEN = Map.of("sync", CATALOGUE_SYNC, "retailer", RETAILER, "operator", OPERATOR);

	@Inject
	DataSource dataSource;

	@ConfigProperty(name = "quarkus.langchain4j.ollama.productAssessment.chat-model.model-id")
	String llmModelId;

	/**
	 * Two archetypes of the seed data.
	 */
	private String archetype;
	private String otherArchetype;

	@BeforeEach
	void setUp() throws SQLException {
		QuarkusMock.installMockForType(new StubTokenHelper(), TokenHelper.class);
		try (var c = dataSource.getConnection(); var s = c.createStatement()) {
			s.execute("DELETE FROM TH_LISTING");
			s.execute("DELETE FROM TH_RETAILER_PRODUCT_NAME");
			s.execute("DELETE FROM TH_RETAILER_PRODUCT");
			s.execute("INSERT INTO TH_RETAILER (id, name) VALUES ('" + RETAILER_ID + "', 'Test Retailer') ON CONFLICT (id) DO NOTHING");
			s.execute("UPDATE TH_RETAILER SET classification_language = NULL WHERE id = '" + RETAILER_ID + "'");
			s.execute("INSERT INTO TH_CATALOGUE (id, retailer_id, country, primary_language) VALUES ('" + CATALOGUE_GR + "', '" + RETAILER_ID + "', 'GR', 'el') ON CONFLICT (id) DO NOTHING");
			s.execute("UPDATE TH_CATALOGUE_CHANGE_CURSOR SET last_sequence = 0");
		}
		var archetypes = rows("SELECT id::text FROM TH_ARCHETYPE_PRODUCT ORDER BY id LIMIT 2");
		archetype = archetypes.get(0);
		otherArchetype = archetypes.get(1);
	}

	@Test
	void theProductsAreFirstPendingWithTheirInputHashAndLanguages() {
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt"));

		as("sync").get(BASE + "/classifications/pending?limit=10")
				.then()
				.statusCode(200)
				.contentType(JSON)
				.body("size()", equalTo(1))
				.body("[0].retailerProductId", equalTo(productId("yoghurt")))
				.body("[0].inputHash", equalTo("h1"))
				.body("[0].languages", equalTo(List.of("el", "en")));
	}

	@Test
	void anAttemptResolvesTheClassificationOfTheProductWhichIsThenNoLongerPending() throws SQLException {
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι"));

		postAttempt("operator", attempt(productId("yoghurt"), "h1", "el", "MANUAL", archetype), 204);

		assertEquals(List.of(archetype + "|h1|false"), classification("yoghurt"));
		assertEquals(List.of("el|MANUAL|test|" + archetype + "|h1"), attempts("yoghurt"));
		pendingCount("operator", 0);
	}

	@Test
	void anAttemptThatNoArchetypeMatchesResolvesToNone() throws SQLException {
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι"));

		postAttempt("sync", attempt(productId("yoghurt"), "h1", "el", "EXPERT", null), 204);

		assertEquals(List.of("null|h1|false"), classification("yoghurt"));
	}

	@Test
	void attemptsThatDisagreeResolveToTheRetailersLanguageForReview() throws SQLException {
		execute("UPDATE TH_RETAILER SET classification_language = 'en' WHERE id = '" + RETAILER_ID + "'");
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt"));
		var id = productId("yoghurt");

		postAttempt("operator", attempt(id, "h1", "en", "EXPERT", otherArchetype), 204);
		postAttempt("operator", attempt(id, "h1", "el", "EXPERT", archetype), 204);

		assertEquals(List.of(otherArchetype + "|h1|true"), classification("yoghurt"));
	}

	@Test
	void attemptsThatDisagreeAreReviewedWithTheNamesOfTheProductAndTheAttemptsTheLatestFirst() throws SQLException {
		execute("UPDATE TH_RETAILER SET classification_language = 'en' WHERE id = '" + RETAILER_ID + "'");
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt"));
		var id = productId("yoghurt");
		postAttempt("operator", attempt(id, "h1", "en", "EXPERT", otherArchetype), 204);
		postAttempt("operator", attempt(id, "h1", "el", "EXPERT", archetype), 204);

		as("sync").get(BASE + "/classifications/review?limit=10")
				.then()
				.statusCode(200)
				.contentType(JSON)
				.body("size()", equalTo(1))
				.body("[0].retailerProductId", equalTo(id))
				.body("[0].names", equalTo(Map.of("el", "Γιαούρτι", "en", "Yoghurt")))
				.body("[0].inputHash", equalTo("h1"))
				.body("[0].archetypeProductId", equalTo(otherArchetype))
				.body("[0].resolvedAt", matchesPattern(ISO_DATE_TIME))
				.body("[0].attempts.language", equalTo(List.of("el", "en")))
				.body("[0].attempts.classifier", equalTo(List.of("EXPERT", "EXPERT")))
				.body("[0].attempts.classifierVersion", equalTo(List.of("test", "test")))
				.body("[0].attempts.archetypeProductId", equalTo(List.of(archetype, otherArchetype)))
				.body("[0].attempts[0].createdAt", matchesPattern(ISO_DATE_TIME));
	}

	/**
	 * The language model's attempt is stored with the path its matching took, which the review shows; the experts' have
	 * none.
	 */
	@Test
	void theReviewShowsThePathTheMatchingOfAnAttemptTook() throws SQLException {
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt"));
		var id = productId("yoghurt");
		execute("INSERT INTO TH_CLASSIFICATION_ATTEMPT (id, retailer_product_id, lang, classifier, classifier_version, archetype_product_id, input_hash, created_at, diagnostics) "
				+ "VALUES (gen_random_uuid(), '" + id + "', 'el', 'LLM', 'test', '" + archetype + "', 'h1', now() - interval '1 minute', "
				+ "'{\"l1Category\": \"Dairy\", \"l2Category\": \"Fermented milk\", \"l3Category\": \"Yoghurts\", \"archetypeProduct\": \"Yoghurt\"}')");
		postAttempt("operator", attempt(id, "h1", "en", "EXPERT", otherArchetype), 204);
		postAttempt("operator", attempt(id, "h1", "el", "EXPERT", archetype), 204);

		as("sync").get(BASE + "/classifications/review?limit=10")
				.then()
				.statusCode(200)
				.body("[0].attempts.classifier", equalTo(List.of("EXPERT", "EXPERT", "LLM")))
				.body("[0].attempts[0].diagnostics", equalTo(null))
				.body("[0].attempts[1].diagnostics", equalTo(null))
				.body("[0].attempts[2].diagnostics", equalTo(Map.of("l1Category", "Dairy", "l2Category", "Fermented milk", "l3Category", "Yoghurts", "archetypeProduct", "Yoghurt")));
	}

	@Test
	void theLlmMatchesAProductToAnArchetypeAndItsAttemptResolvesTheClassification() throws SQLException {
		givenTheLlmReplies(new AiSelection.Match(0));
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι"));
		var id = productId("yoghurt");

		var response = postLlmClassification("sync", llmClassification(id, "el"), 200)
				.contentType(JSON)
				.body("outcome", equalTo("MATCHED"))
				.body("attempt.language", equalTo("el"))
				.body("attempt.classifier", equalTo("LLM"))
				.body("attempt.classifierVersion", equalTo(llmModelId + "/prompt-1/glossary-1"))
				.body("attempt.createdAt", matchesPattern(ISO_DATE_TIME))
				.body("classification.inputHash", equalTo("h1"))
				.body("classification.needsReview", equalTo(false))
				.body("classification.resolvedAt", matchesPattern(ISO_DATE_TIME))
				.extract();

		String matched = response.path("attempt.archetypeProductId");
		assertEquals(matched, response.<String>path("classification.archetypeProductId"));
		assertEquals(response.<Map<String, String>>path("attempt.diagnostics"), response.<Map<String, String>>path("diagnostics"));
		assertEquals(rows("SELECT name FROM TH_ARCHETYPE_PRODUCT WHERE id = '" + matched + "'"), List.of(response.<String>path("diagnostics.archetypeProduct")));
		assertEquals(List.of(matched + "|h1|false"), classification("yoghurt"));
		assertEquals(List.of("el|LLM|" + llmModelId + "/prompt-1/glossary-1|" + matched + "|h1"), attempts("yoghurt"));
		pendingCount("sync", 0);
	}

	@Test
	void theLlmFindingNoArchetypeForAProductResolvesItsClassificationToNone() throws SQLException {
		givenTheLlmReplies(new AiSelection.None());
		applyProduct("soap", 1, "h1", Map.of("el", "Σαπούνι"));

		postLlmClassification("operator", llmClassification(productId("soap"), "el"), 200)
				.body("outcome", equalTo("NO_MATCH"))
				.body("attempt.archetypeProductId", equalTo(null))
				.body("classification.archetypeProductId", equalTo(null))
				.body("diagnostics", equalTo(NO_LEVEL_REACHED));

		assertEquals(List.of("null|h1|false"), classification("soap"));
	}

	/**
	 * The malformed reply is logged as a warning, captured here.
	 */
	@Test
	void aMalformedReplyOfTheLlmIsAnsweredWithNothingRecordedAndTheProductStillPending() throws SQLException {
		givenTheLlmReplies(new AiSelection.Malformed("42"));
		applyProduct("soap", 1, "h1", Map.of("el", "Σαπούνι"));
		var warnings = new ArrayList<String>();

		capturingTheWarningsOf(ArchetypeMatching.class, warnings, () -> postLlmClassification("operator", llmClassification(productId("soap"), "el"), 200)
				.body("outcome", equalTo("MALFORMED_REPLY"))
				.body("attempt", equalTo(null))
				.body("classification", equalTo(null))
				.body("diagnostics", equalTo(NO_LEVEL_REACHED)));

		assertEquals(List.of("AI selection was not a valid candidate number, kind: category, raw reply: '42'"), warnings);
		assertNothingRecorded();
		pendingCount("operator", 1);
	}

	@Test
	void aProductIsNotClassifiedWithTheLlmFromALanguageItIsNotNamedInOrIfItIsUnknown() throws SQLException {
		givenTheLlmReplies(new AiSelection.Match(0));
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι"));
		var id = productId("yoghurt");

		postLlmClassification("operator", llmClassification(id, "en"), 400)
				.body("message", equalTo("The value of input 'language' is illegal."));
		postLlmClassification("operator", llmClassification("00000000-0000-0000-0000-000000000000", "el"), 404);
		postLlmClassification("operator", llmClassification("not-a-uuid", "el"), 400);
		postLlmClassification("operator", llmClassification(id, null), 400);
		postLlmClassification("retailer", llmClassification(id, "el"), 403);

		assertNothingRecorded();
	}

	@Test
	void aManualAttemptSettlesAReview() {
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt"));
		var id = productId("yoghurt");
		postAttempt("operator", attempt(id, "h1", "en", "EXPERT", otherArchetype), 204);
		postAttempt("operator", attempt(id, "h1", "el", "EXPERT", archetype), 204);

		postAttempt("operator", attempt(id, "h1", "el", "MANUAL", archetype), 204);

		as("operator").get(BASE + "/classifications/review?limit=10").then().statusCode(200).body("size()", equalTo(0));
	}

	@Test
	void aProductChangedAfterItsClassificationIsPendingAgain() {
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι"));
		postAttempt("operator", attempt(productId("yoghurt"), "h1", "el", "MANUAL", archetype), 204);

		applyProduct("yoghurt", 2, "h2", Map.of("el", "Γιαούρτι στραγγιστό"));

		as("sync").get(BASE + "/classifications/pending?limit=10")
				.then()
				.statusCode(200)
				.body("size()", equalTo(1))
				.body("[0].inputHash", equalTo("h2"));
	}

	@Test
	void anAttemptMadeFromAStaleInputHashIsAConflict() throws SQLException {
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι"));
		var id = productId("yoghurt");
		applyProduct("yoghurt", 2, "h2", Map.of("el", "Γιαούρτι στραγγιστό"));

		postAttempt("operator", attempt(id, "h1", "el", "MANUAL", archetype), 409)
				.body("message", equalTo("The input hash is not the product's current one, product id: " + id + ", inputHash: h1"));

		assertNothingRecorded();
	}

	@Test
	void anAttemptOfAnUnknownProductOrArchetypeIsNotFound() throws SQLException {
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι"));

		postAttempt("operator", attempt("00000000-0000-0000-0000-000000000000", "h1", "el", "MANUAL", archetype), 404);
		postAttempt("operator", attempt(productId("yoghurt"), "h1", "el", "MANUAL", "00000000-0000-0000-0000-000000000000"), 404);

		assertNothingRecorded();
	}

	@Test
	void malformedAttemptsAreRefused() throws SQLException {
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι"));
		var id = productId("yoghurt");

		postAttempt("operator", attempt(id, "h1", "el", "LLM", archetype), 400);
		postAttempt("operator", attempt(id, "h1", "en", "MANUAL", archetype), 400);
		postAttempt("operator", attempt(id, "h1", "el", "WP3", archetype), 400);
		postAttempt("operator", attempt("not-a-uuid", "h1", "el", "MANUAL", archetype), 400);
		postAttempt("operator", attempt(id, "h1", "el", "MANUAL", "not-a-uuid"), 400);
		postAttempt("operator", attempt(id, null, "el", "MANUAL", archetype), 400);

		assertNothingRecorded();
	}

	@Test
	void thePendingProductsAndTheClassificationsToReviewAreFoundFromOneToAThousandAtATime() {
		for (var path : List.of("/classifications/pending", "/classifications/review")) {
			for (var limit : List.of("0", "1001")) {
				as("sync").get(BASE + path + "?limit=" + limit).then().statusCode(400);
			}
			as("sync").get(BASE + path).then().statusCode(400);
		}
	}

	@Test
	void callersOtherThanTheSyncServiceAccountAndOperatorsAreRefused() throws SQLException {
		applyProduct("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι"));

		as("retailer").get(BASE + "/classifications/pending?limit=10").then().statusCode(403);
		as("retailer").get(BASE + "/classifications/review?limit=10").then().statusCode(403);
		postAttempt("retailer", attempt(productId("yoghurt"), "h1", "el", "MANUAL", archetype), 403);

		assertNothingRecorded();
	}

	private static RequestSpecification as(String token) {
		return RestAssured.given().header("Authorization", "Bearer " + token);
	}

	private static ValidatableResponse postAttempt(String token, Map<String, Object> attempt, int expectedStatus) {
		return as(token).contentType(JSON).body(attempt).post(BASE + "/classification-attempts").then().statusCode(expectedStatus);
	}

	private static ValidatableResponse postLlmClassification(String token, Map<String, Object> request, int expectedStatus) {
		return as(token).contentType(JSON).body(request).post(BASE + "/llm-classifications").then().statusCode(expectedStatus);
	}

	private static Map<String, Object> llmClassification(String retailerProductId, String language) {
		var request = new HashMap<String, Object>();
		request.put("retailerProductId", retailerProductId);
		request.put("language", language);
		return request;
	}

	/**
	 * The language model answers every question of the matching with the same reply.
	 */
	private static void givenTheLlmReplies(AiSelection reply) {
		QuarkusMock.installMockForType(new StubProductAssessmentAiFacade(reply), ProductAssessmentAiFacade.class);
	}

	/**
	 * Run the work with the warnings of the class captured instead of printed.
	 */
	private static void capturingTheWarningsOf(Class<?> loggingClass, List<String> warnings, Runnable work) {
		var handler = new Handler() {
			@Override
			public void publish(LogRecord record) {
				if (record.getLevel().intValue() == Level.WARNING.intValue()) warnings.add(record.getMessage());
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		};
		var logger = LogContext.getLogContext().getLogger(loggingClass.getName());
		logger.addHandler(handler);
		logger.setUseParentHandlers(false);
		try {
			work.run();
		} finally {
			logger.removeHandler(handler);
			logger.setUseParentHandlers(true);
		}
	}

	private static void pendingCount(String token, int count) {
		as(token).get(BASE + "/classifications/pending?limit=10").then().statusCode(200).body("size()", equalTo(count));
	}

	private static Map<String, Object> attempt(String retailerProductId, String inputHash, String language, String classifier, String archetypeProductId) {
		var attempt = new HashMap<String, Object>();
		attempt.put("retailerProductId", retailerProductId);
		attempt.put("inputHash", inputHash);
		attempt.put("language", language);
		attempt.put("classifier", classifier);
		attempt.put("classifierVersion", "test");
		attempt.put("archetypeProductId", archetypeProductId);
		return attempt;
	}

	/**
	 * Apply the product as the next change of the Catalogue Sync.
	 */
	private void applyProduct(String productKey, long changeSequence, String inputHash, Map<String, String> names) {
		var product = new HashMap<String, Object>();
		product.put("retailerId", RETAILER_ID);
		product.put("productKey", productKey);
		product.put("changeSequence", changeSequence);
		product.put("inputHash", inputHash);
		product.put("names", names);
		product.put("characteristics", Map.of());
		product.put("tags", List.of());
		product.put("listings", List.of(Map.of("catalogueId", CATALOGUE_GR, "price", new BigDecimal("1.99"), "currency", "EUR", "active", true)));
		product.put("removed", false);
		as("sync").contentType(JSON)
				.body(Map.of("fromSequence", changeSequence - 1, "toSequence", changeSequence, "products", List.of(product)))
				.post(BASE + "/catalogue-changes")
				.then().statusCode(204);
	}

	private String productId(String productKey) {
		try {
			return rows("SELECT id::text FROM TH_RETAILER_PRODUCT WHERE product_key = '" + productKey + "'").getFirst();
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	private List<String> classification(String productKey) throws SQLException {
		return rows("SELECT c.archetype_product_id, c.input_hash, c.needs_review::text FROM TH_CLASSIFICATION c "
				+ "JOIN TH_RETAILER_PRODUCT p ON p.id = c.retailer_product_id WHERE p.product_key = '" + productKey + "'");
	}

	private List<String> attempts(String productKey) throws SQLException {
		return rows("SELECT a.lang, a.classifier, a.classifier_version, a.archetype_product_id, a.input_hash FROM TH_CLASSIFICATION_ATTEMPT a "
				+ "JOIN TH_RETAILER_PRODUCT p ON p.id = a.retailer_product_id WHERE p.product_key = '" + productKey + "' ORDER BY a.created_at");
	}

	private void assertNothingRecorded() throws SQLException {
		assertEquals(List.of("0|0"), rows("SELECT (SELECT count(*) FROM TH_CLASSIFICATION_ATTEMPT), (SELECT count(*) FROM TH_CLASSIFICATION)"));
	}

	private void execute(String sql) throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement()) {
			s.execute(sql);
		}
	}

	/**
	 * @return Each row, its columns joined by {@code |}
	 */
	private List<String> rows(String sql) throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			var rows = new ArrayList<String>();
			var columns = rs.getMetaData().getColumnCount();
			while (rs.next()) {
				var row = new StringJoiner("|");
				for (int i = 1; i <= columns; i++) {
					row.add(rs.getString(i) != null ? rs.getString(i) : "null");
				}
				rows.add(row.toString());
			}
			return rows;
		}
	}

	private static final class StubProductAssessmentAiFacade extends ProductAssessmentAiFacadeImpl {
		private final AiSelection reply;

		StubProductAssessmentAiFacade(AiSelection reply) {
			super(null, null);
			this.reply = reply;
		}

		@Override
		public Uni<AiSelection> extractL1Category(ProductBaseData productData, List<String> categories, List<FoodTerm> recognizedTerms) {
			return Uni.createFrom().item(reply);
		}

		@Override
		public Uni<AiSelection> extractSubcategory(ProductBaseData productData, List<String> categories, List<FoodTerm> recognizedTerms) {
			return Uni.createFrom().item(reply);
		}

		@Override
		public Uni<AiSelection> extractArchetypeProduct(ProductBaseData productData, List<String> products, List<FoodTerm> recognizedTerms, String categoryGuidance) {
			return Uni.createFrom().item(reply);
		}
	}

	private static final class StubTokenHelper extends TokenHelperImpl {
		StubTokenHelper() {
			super(null, null, null, null, null);
		}

		@Override
		public Uni<User> processToken(String token) {
			return Uni.createFrom().item(USERS_BY_TOKEN.get(token));
		}

		@Override
		public User makeUnauthenticated() {
			return new UserImpl(null, null, null, false, true, Set.of());
		}
	}
}
