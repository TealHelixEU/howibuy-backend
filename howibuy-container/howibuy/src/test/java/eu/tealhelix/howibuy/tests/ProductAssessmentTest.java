package eu.tealhelix.howibuy.tests;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.test.quarkus.PostgresTestResource;
import eu.tealhelix.common.v1.model.Role;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.jaxrs.authentication.jwt.Authentication;
import eu.tealhelix.howibuy.jaxrs.authentication.jwt.TokenHelper;
import eu.tealhelix.howibuy.jaxrs.authentication.jwt.TokenHelperImpl;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.services.model.FoodTerm;
import eu.tealhelix.howibuy.services.v1.ai.AiSelection;
import eu.tealhelix.howibuy.services.v1.ai.ProductAssessmentAiFacade;
import eu.tealhelix.howibuy.services.v1.ai.impl.ProductAssessmentAiFacadeImpl;
import eu.tealhelix.howibuy.services.v1.impl.ArchetypeCorpus;
import eu.tealhelix.howibuy.v1.model.ProductBaseData;
import eu.tealhelix.howibuy.v1.types.AlternativeForProductType;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Assessing the products a user scans in a Catalogue against a real database, with the products brought in through the
 * Catalogue Changes as the Catalogue Sync brings them, and classified through Classification Attempts. JWT decoding is
 * faked (a stubbed {@link TokenHelper}), and the language model is replaced by one that fails the request if the
 * assessment asks it anything.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, initArgs = @ResourceArg(name = "contexts", value = "appdata"))
public class ProductAssessmentTest {
	private static final String BASE = "/api/howibuy/v1";

	private static final String RETAILER_ID = "6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11";
	private static final String CATALOGUE_GR = "test-gr";
	private static final String CATALOGUE_CY = "test-cy";

	private static final User CATALOGUE_SYNC = new UserImpl(new UserIdImpl("1d0c6b0e-3b5f-4d2a-8f7e-2a9c4b6d8e33"), "catalogue-sync", null, false, false, Set.of(Role.CATALOGUE_SYNC));
	private static final User OPERATOR = new UserImpl(new UserIdImpl("4e2d8c1a-7b3f-4a6e-9c5d-3f1b7a9e2c44"), "operator", null, false, true, Set.of(Role.OPERATOR));
	private static final User SHOPPER = new UserImpl(new UserIdImpl("8b5e2a17-4c9d-4f3e-a1b6-7d0c3e9f2a55"), "shopper", null, false, true, Set.of());

	/**
	 * The shopper scans in the Greek Catalogue with a token of the token exchange, and without a Catalogue with any other
	 * token.
	 */
	private static final Map<String, Authentication> AUTHENTICATIONS_BY_TOKEN = Map.of(
			"sync", new Authentication(CATALOGUE_SYNC, null),
			"operator", new Authentication(OPERATOR, null),
			"shopper", new Authentication(SHOPPER, new CatalogueIdImpl(CATALOGUE_GR)),
			"shopper-without-catalogue", new Authentication(SHOPPER, null));

	@Inject
	DataSource dataSource;

	@Inject
	ArchetypeCorpus archetypeCorpus;

	/**
	 * An archetype of the seed data.
	 */
	private String archetype;

	@BeforeEach
	void setUp() throws SQLException {
		QuarkusMock.installMockForType(new StubTokenHelper(), TokenHelper.class);
		QuarkusMock.installMockForType(new UnreachableProductAssessmentAiFacade(), ProductAssessmentAiFacade.class);
		try (var c = dataSource.getConnection(); var s = c.createStatement()) {
			s.execute("DELETE FROM TH_LISTING");
			s.execute("DELETE FROM TH_RETAILER_PRODUCT_NAME");
			s.execute("DELETE FROM TH_RETAILER_PRODUCT");
			s.execute("INSERT INTO TH_RETAILER (id, name) VALUES ('" + RETAILER_ID + "', 'Test Retailer') ON CONFLICT (id) DO NOTHING");
			s.execute("UPDATE TH_RETAILER SET classification_language = NULL WHERE id = '" + RETAILER_ID + "'");
			s.execute("INSERT INTO TH_CATALOGUE (id, retailer_id, country, primary_language) VALUES ('" + CATALOGUE_GR + "', '" + RETAILER_ID + "', 'GR', 'el') ON CONFLICT (id) DO NOTHING");
			s.execute("INSERT INTO TH_CATALOGUE (id, retailer_id, country, primary_language) VALUES ('" + CATALOGUE_CY + "', '" + RETAILER_ID + "', 'CY', 'el') ON CONFLICT (id) DO NOTHING");
			s.execute("UPDATE TH_CATALOGUE_CHANGE_CURSOR SET last_sequence = 0");
		}
		archetype = rows("SELECT id::text FROM TH_ARCHETYPE_PRODUCT ORDER BY id LIMIT 1").getFirst();
	}

	@Test
	void aClassifiedProductIsAssessedWithItsArchetypeAndHowItWasIdentified() throws SQLException {
		applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		classify("yoghurt", "h1", archetype);

		assessSingle("shopper", single("yoghurt", "el"), 200)
				.contentType(JSON)
				.body("productKey", equalTo("yoghurt"))
				.body("type", equalTo("SUCCESS"))
				.body("failureReason", equalTo(null))
				.body("bestPersonalAlternative.type", notNullValue())
				.body("diagnostics", equalTo(classified("yoghurt", false)));
	}

	@Test
	void aProductOfTheRetailerThatIsNotKnownIsUnknown() {
		applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));

		assessSingle("shopper", single("soap", "el"), 200)
				.body("productKey", equalTo("soap"))
				.body("type", equalTo("FAILURE_TO_IDENTIFY"))
				.body("failureReason", equalTo("UNKNOWN_PRODUCT"))
				.body("diagnostics", equalTo(null))
				.body("bestPersonalAlternative", equalTo(null));
	}

	@Test
	void aProductThatWasNeverClassifiedIsNotClassified() {
		applyProduct("honey", 1, "h1", listing(CATALOGUE_GR, true));

		assessSingle("shopper", single("honey", "el"), 200)
				.body("type", equalTo("FAILURE_TO_IDENTIFY"))
				.body("failureReason", equalTo("NOT_CLASSIFIED"))
				.body("diagnostics", equalTo(notClassified("honey")));
	}

	@Test
	void aProductThatNoArchetypeMatchesHasNoMatchingArchetype() {
		applyProduct("soap", 1, "h1", listing(CATALOGUE_GR, true));
		classify("soap", "h1", null);

		assessSingle("shopper", single("soap", "el"), 200)
				.body("type", equalTo("FAILURE_TO_IDENTIFY"))
				.body("failureReason", equalTo("NO_MATCHING_ARCHETYPE"))
				.body("diagnostics", equalTo(noMatchingArchetype("soap")));
	}

	@Test
	void aProductIsAssessedWhetherItsListingIsInactiveOrItIsListedOnlyInAnotherCatalogue() throws SQLException {
		applyProduct("feta", 1, "h1", listing(CATALOGUE_GR, false));
		applyProduct("halloumi", 2, "h2", listing(CATALOGUE_CY, true));
		classify("feta", "h1", archetype);
		classify("halloumi", "h2", archetype);

		assessSingle("shopper", single("feta", "el"), 200)
				.body("type", equalTo("SUCCESS"))
				.body("diagnostics", equalTo(classified("feta", false)));
		assessSingle("shopper", single("halloumi", "el"), 200)
				.body("type", equalTo("SUCCESS"))
				.body("diagnostics", equalTo(classified("halloumi", false)));
	}

	/**
	 * A product that changed since it was classified waits for its Classification to be resolved again, and is assessed
	 * with the archetype it has meanwhile.
	 */
	@Test
	void aProductThatChangedSinceItWasClassifiedIsAssessedWithItsArchetypeAndPending() throws SQLException {
		applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		classify("yoghurt", "h1", archetype);
		applyProduct("yoghurt", 2, "h2", listing(CATALOGUE_GR, true));

		assessSingle("shopper", single("yoghurt", "el"), 200)
				.body("type", equalTo("SUCCESS"))
				.body("diagnostics", equalTo(classified("yoghurt", true)));
	}

	/**
	 * Nothing else in the Catalogue, the scanned product is the best the user can buy there, whether its own Listing is
	 * active or not.
	 */
	@Test
	void aProductIsGoodEnoughWhenTheCatalogueStocksNothingBetterEvenWithItsListingInactive() throws SQLException {
		var archetypes = anArchetypeAndOneThatWouldRankFirstInItsPlace();
		applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, false));
		classify("yoghurt", "h1", archetypes.reference().asString());

		assessSingle("shopper", single("yoghurt", "el"), 200)
				.body("type", equalTo("SUCCESS"))
				.body("bestScientificAlternative.type", equalTo("GOOD_ENOUGH"))
				.body("bestScientificAlternative.archetype.id", equalTo(archetypes.reference().asString()))
				.body("bestScientificAlternative.product", equalTo(null));
	}

	/**
	 * The price is written as a plain number without its trailing zeros: 2, rather than 2.0000 or 2E+0.
	 */
	@Test
	void aSuggestionNamesTheCheapestProductOfItsArchetypeActivelyListedInTheCatalogue() throws SQLException {
		var archetypes = anArchetypeAndOneThatWouldRankFirstInItsPlace();
		applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		applyProduct("kefir", 2, "h2", listing(CATALOGUE_GR, true, "2.0000"));
		applyProduct("kefir-organic", 3, "h3", listing(CATALOGUE_GR, true, "3.4900"));
		applyProduct("kefir-on-offer", 4, "h4", listing(CATALOGUE_GR, false, "0.9900"));
		applyProduct("kefir-in-cyprus", 5, "h5", listing(CATALOGUE_CY, true, "0.8900"));
		classify("yoghurt", "h1", archetypes.reference().asString());
		for (var product : Map.of("kefir", "h2", "kefir-organic", "h3", "kefir-on-offer", "h4", "kefir-in-cyprus", "h5").entrySet()) {
			classify(product.getKey(), product.getValue(), archetypes.better().asString());
		}

		var body = assessSingle("shopper", single("yoghurt", "el"), 200)
				.body("bestScientificAlternative.type", equalTo("SUGGESTION"))
				.body("bestScientificAlternative.archetype.id", equalTo(archetypes.better().asString()))
				.body("bestScientificAlternative.product.productKey", equalTo("kefir"))
				.body("bestScientificAlternative.product.name", equalTo("kefir"))
				.body("bestScientificAlternative.product.language", equalTo("el"))
				.body("bestScientificAlternative.product.currency", equalTo("EUR"))
				.extract().asString();

		assertTrue(Pattern.compile("\"price\":2[,}]").matcher(body).find(), "the price as a plain number: " + body);
	}

	@Test
	void aSuggestedProductIsNamedInTheLanguageAskedForElseInTheCataloguesPrimaryLanguage() throws SQLException {
		var archetypes = anArchetypeAndOneThatWouldRankFirstInItsPlace();
		applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		applyProduct("kefir", 2, "h2", Map.of("el", "Κεφίρ", "en", "Kefir"), listing(CATALOGUE_GR, true));
		classify("yoghurt", "h1", archetypes.reference().asString());
		classify("kefir", "h2", archetypes.better().asString());

		assessSingle("shopper", single("yoghurt", "en"), 200)
				.body("bestScientificAlternative.product.name", equalTo("Kefir"))
				.body("bestScientificAlternative.product.language", equalTo("en"));
		assessSingle("shopper", single("yoghurt", "de"), 200)
				.body("bestScientificAlternative.product.name", equalTo("Κεφίρ"))
				.body("bestScientificAlternative.product.language", equalTo("el"));
	}

	@Test
	void anArchetypeIsSuggestedOnlyWhileAProductOfItIsActivelyListedInTheCatalogue() throws SQLException {
		var archetypes = anArchetypeAndOneThatWouldRankFirstInItsPlace();
		applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		applyProduct("kefir", 2, "h2", listing(CATALOGUE_GR, true));
		classify("yoghurt", "h1", archetypes.reference().asString());
		classify("kefir", "h2", archetypes.better().asString());

		assessSingle("shopper", single("yoghurt", "el"), 200)
				.body("bestScientificAlternative.type", equalTo("SUGGESTION"))
				.body("bestScientificAlternative.archetype.id", equalTo(archetypes.better().asString()));

		applyProduct("kefir", 3, "h2", listing(CATALOGUE_GR, false));

		assessSingle("shopper", single("yoghurt", "el"), 200)
				.body("bestScientificAlternative.type", equalTo("GOOD_ENOUGH"))
				.body("bestScientificAlternative.archetype.id", equalTo(archetypes.reference().asString()));
	}

	@Test
	void anArchetypeListedOnlyInAnotherCatalogueIsNeverSuggested() throws SQLException {
		var archetypes = anArchetypeAndOneThatWouldRankFirstInItsPlace();
		applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		applyProduct("kefir", 2, "h2", listing(CATALOGUE_CY, true));
		classify("yoghurt", "h1", archetypes.reference().asString());
		classify("kefir", "h2", archetypes.better().asString());

		assessSingle("shopper", single("yoghurt", "el"), 200)
				.body("bestScientificAlternative.type", equalTo("GOOD_ENOUGH"))
				.body("bestScientificAlternative.archetype.id", equalTo(archetypes.reference().asString()));
	}

	@Test
	void aBatchIsAnsweredOneOutcomePerKeySentInTheOrderSent() {
		givenProductsOfEveryKind();
		var request = multi("el", "yoghurt", "unknown", "honey", "soap", "feta", "yoghurt");

		as("shopper").contentType(JSON).body(request).post(BASE + "/assessment/product/multi")
				.then()
				.statusCode(200)
				.contentType(JSON)
				.body("assessments.productKey", equalTo(List.of("yoghurt", "unknown", "honey", "soap", "feta", "yoghurt")))
				.body("assessments.type", equalTo(List.of("SUCCESS", "FAILURE_TO_IDENTIFY", "FAILURE_TO_IDENTIFY", "FAILURE_TO_IDENTIFY", "SUCCESS", "SUCCESS")))
				.body("assessments.failureReason", equalTo(Arrays.asList(null, "UNKNOWN_PRODUCT", "NOT_CLASSIFIED", "NO_MATCHING_ARCHETYPE", null, null)));
	}

	/**
	 * The stream has no enclosing array, so its outcomes are told apart by their keys and reasons alone.
	 */
	@Test
	void aStreamedBatchIsAnsweredOneOutcomePerKeySentInTheOrderSent() {
		givenProductsOfEveryKind();
		var request = multi("el", "yoghurt", "unknown", "honey", "soap", "feta");

		var body = as("shopper").contentType(JSON).body(request).post(BASE + "/assessment/product/multi-async")
				.then()
				.statusCode(200)
				.extract().asString();

		assertEquals(List.of("\"yoghurt\"", "\"unknown\"", "\"honey\"", "\"soap\"", "\"feta\""), valuesOf("productKey", body));
		assertEquals(List.of("null", "\"UNKNOWN_PRODUCT\"", "\"NOT_CLASSIFIED\"", "\"NO_MATCHING_ARCHETYPE\"", "null"), valuesOf("failureReason", body));
	}

	@Test
	void aBatchOfTheMostProductsIsAssessedAndOneMoreIsRefused() {
		var keys = new ArrayList<>(Collections.nCopies(400, "unknown"));

		as("shopper").contentType(JSON).body(multi("el", keys.toArray(String[]::new))).post(BASE + "/assessment/product/multi")
				.then().statusCode(200).body("assessments.size()", equalTo(400));
		keys.add("unknown");
		for (var path : List.of("/assessment/product/multi", "/assessment/product/multi-async")) {
			as("shopper").contentType(JSON).body(multi("el", keys.toArray(String[]::new))).post(BASE + path)
					.then().statusCode(400).body("message", equalTo("The value of input 'productKeys' is illegal."));
		}
	}

	@Test
	void anAssessmentWithoutALanguageOrAProductKeyIsRefused() {
		assessSingle("shopper", single("yoghurt", null), 400)
				.body("message", equalTo("Required input 'language' is missing."));
		assessSingle("shopper", single(null, "el"), 400)
				.body("message", equalTo("Required input 'productKey' is missing."));
		for (var path : List.of("/assessment/product/multi", "/assessment/product/multi-async")) {
			as("shopper").contentType(JSON).body(multi(null, "yoghurt")).post(BASE + path)
					.then().statusCode(400).body("message", equalTo("Required input 'language' is missing."));
			as("shopper").contentType(JSON).body(multi("el", "yoghurt", null)).post(BASE + path)
					.then().statusCode(400).body("message", equalTo("Required input 'productKeys' is missing."));
			as("shopper").contentType(JSON).body(Map.of("language", "el")).post(BASE + path)
					.then().statusCode(400).body("message", equalTo("Required input 'productKeys' is missing."));
		}
	}

	@Test
	void anAssessmentWhoseTokenNamesNoCatalogueIsForbidden() {
		assessSingle("shopper-without-catalogue", single("yoghurt", "el"), 403);
		for (var path : List.of("/assessment/product/multi", "/assessment/product/multi-async")) {
			as("shopper-without-catalogue").contentType(JSON).body(multi("el", "yoghurt")).post(BASE + path).then().statusCode(403);
		}
	}

	private record ArchetypePair(ArchetypeProductId reference, ArchetypeProductId better) {
	}

	/**
	 * @return An archetype of the seed data and the one the scientific ranking puts first in its place when every
	 *         archetype is stocked
	 */
	private ArchetypePair anArchetypeAndOneThatWouldRankFirstInItsPlace() throws SQLException {
		var archetypes = archetypeCorpus.scoredArchetypes().await().atMost(Duration.ofSeconds(60));
		Set<ArchetypeProductId> all = rows("SELECT id::text FROM TH_ARCHETYPE_PRODUCT").stream()
				.map(ArchetypeProductIdImpl::new)
				.collect(Collectors.toSet());
		return all.stream().sorted(Comparator.comparing(ArchetypeProductId::asString))
				.flatMap(reference -> {
					var scientific = archetypes.recommendationsFor(reference, ScientificWeights.profile(), all).scientific();
					return scientific.getType() == AlternativeForProductType.SUGGESTION
							? Stream.of(new ArchetypePair(reference, scientific.getArchetype().getId()))
							: Stream.empty();
				})
				.findFirst()
				.orElseThrow();
	}

	/**
	 * One product of each kind: classified, never classified, matching no archetype, and classified but listed Inactive.
	 */
	private void givenProductsOfEveryKind() {
		applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		applyProduct("honey", 2, "h2", listing(CATALOGUE_GR, true));
		applyProduct("soap", 3, "h3", listing(CATALOGUE_GR, true));
		applyProduct("feta", 4, "h4", listing(CATALOGUE_GR, false));
		classify("yoghurt", "h1", archetype);
		classify("soap", "h3", null);
		classify("feta", "h4", archetype);
	}

	private Map<String, Object> classified(String productKey, boolean pending) throws SQLException {
		var diagnostics = noMatchingArchetype(productKey);
		diagnostics.put("pending", pending);
		diagnostics.put("archetypeProductId", archetype);
		diagnostics.put("archetypeProductName", rows("SELECT name FROM TH_ARCHETYPE_PRODUCT WHERE id = '" + archetype + "'").getFirst());
		return diagnostics;
	}

	private Map<String, Object> noMatchingArchetype(String productKey) {
		var diagnostics = notClassified(productKey);
		diagnostics.put("classifier", "EXPERT");
		diagnostics.put("needsReview", false);
		diagnostics.put("pending", false);
		diagnostics.put("archetypeProductId", null);
		diagnostics.put("archetypeProductName", null);
		return diagnostics;
	}

	private Map<String, Object> notClassified(String productKey) {
		var diagnostics = new HashMap<String, Object>();
		diagnostics.put("retailerProductId", productId(productKey));
		diagnostics.put("classifier", null);
		diagnostics.put("needsReview", null);
		diagnostics.put("pending", null);
		diagnostics.put("archetypeProductId", null);
		diagnostics.put("archetypeProductName", null);
		return diagnostics;
	}

	/**
	 * @return The JSON values of the field in the order they appear in the body
	 */
	private static List<String> valuesOf(String field, String body) {
		var values = new ArrayList<String>();
		var matcher = Pattern.compile("\"" + field + "\":(null|\"[^\"]*\")").matcher(body);
		while (matcher.find()) values.add(matcher.group(1));
		return values;
	}

	private static RequestSpecification as(String token) {
		return RestAssured.given().header("Authorization", "Bearer " + token);
	}

	private static ValidatableResponse assessSingle(String token, Map<String, Object> request, int expectedStatus) {
		return as(token).contentType(JSON).body(request).post(BASE + "/assessment/product/single").then().statusCode(expectedStatus);
	}

	private static Map<String, Object> single(String productKey, String language) {
		var request = new HashMap<String, Object>();
		request.put("productKey", productKey);
		request.put("language", language);
		return request;
	}

	private static Map<String, Object> multi(String language, String... productKeys) {
		var request = new HashMap<String, Object>();
		request.put("language", language);
		request.put("productKeys", Arrays.asList(productKeys));
		return request;
	}

	/**
	 * An expert's attempt resolves the Classification of the product.
	 */
	private void classify(String productKey, String inputHash, String archetypeProductId) {
		var attempt = new HashMap<String, Object>();
		attempt.put("retailerProductId", productId(productKey));
		attempt.put("inputHash", inputHash);
		attempt.put("language", "el");
		attempt.put("classifier", "EXPERT");
		attempt.put("classifierVersion", "test");
		attempt.put("archetypeProductId", archetypeProductId);
		as("operator").contentType(JSON).body(attempt).post(BASE + "/classification-attempts").then().statusCode(204);
	}

	private static Map<String, Object> listing(String catalogueId, boolean active) {
		return listing(catalogueId, active, "1.99");
	}

	private static Map<String, Object> listing(String catalogueId, boolean active, String price) {
		return Map.of("catalogueId", catalogueId, "price", new BigDecimal(price), "currency", "EUR", "active", active);
	}

	/**
	 * Apply the product, named in Greek by its key, as the next change of the Catalogue Sync.
	 */
	private void applyProduct(String productKey, long changeSequence, String inputHash, Map<String, Object> listing) {
		applyProduct(productKey, changeSequence, inputHash, Map.of("el", productKey), listing);
	}

	/**
	 * Apply the product as the next change of the Catalogue Sync.
	 */
	private void applyProduct(String productKey, long changeSequence, String inputHash, Map<String, String> names, Map<String, Object> listing) {
		var product = new HashMap<String, Object>();
		product.put("retailerId", RETAILER_ID);
		product.put("productKey", productKey);
		product.put("changeSequence", changeSequence);
		product.put("inputHash", inputHash);
		product.put("names", names);
		product.put("characteristics", Map.of());
		product.put("tags", List.of());
		product.put("listings", List.of(listing));
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

	/**
	 * Fails the request of an assessment that asks the language model anything.
	 */
	private static final class UnreachableProductAssessmentAiFacade extends ProductAssessmentAiFacadeImpl {
		UnreachableProductAssessmentAiFacade() {
			super(null, null);
		}

		@Override
		public Uni<AiSelection> extractL1Category(ProductBaseData productData, List<String> categories, List<FoodTerm> recognizedTerms) {
			throw new AssertionError("The assessment asked the language model");
		}

		@Override
		public Uni<AiSelection> extractSubcategory(ProductBaseData productData, List<String> categories, List<FoodTerm> recognizedTerms) {
			throw new AssertionError("The assessment asked the language model");
		}

		@Override
		public Uni<AiSelection> extractArchetypeProduct(ProductBaseData productData, List<String> products, List<FoodTerm> recognizedTerms, String categoryGuidance) {
			throw new AssertionError("The assessment asked the language model");
		}
	}

	private static final class StubTokenHelper extends TokenHelperImpl {
		StubTokenHelper() {
			super(null, null, null, null, null);
		}

		@Override
		public Uni<Authentication> processToken(String token) {
			return Uni.createFrom().item(AUTHENTICATIONS_BY_TOKEN.get(token));
		}

		@Override
		public User makeUnauthenticated() {
			return new UserImpl(null, null, null, false, true, Set.of());
		}
	}
}
