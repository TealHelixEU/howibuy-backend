package eu.tealhelix.howibuy.tests;

import static eu.tealhelix.howibuy.tests.AssessmentFixtures.BASE;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.CATALOGUE_CY;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.CATALOGUE_GR;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.as;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.listing;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.test.quarkus.PostgresTestResource;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.jaxrs.authentication.jwt.Authentication;
import eu.tealhelix.howibuy.jaxrs.authentication.jwt.TokenHelper;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.services.v1.ai.ProductAssessmentAiFacade;
import eu.tealhelix.howibuy.services.v1.impl.ArchetypeCorpus;
import eu.tealhelix.howibuy.v1.types.AlternativeForProductType;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
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
	private static final User SHOPPER = new UserImpl(new UserIdImpl("8b5e2a17-4c9d-4f3e-a1b6-7d0c3e9f2a55"), "shopper", null, false, true, Set.of());

	/**
	 * The shopper scans in the Greek Catalogue with a token of the token exchange, and without a Catalogue with any other
	 * token.
	 */
	private static final Map<String, Authentication> AUTHENTICATIONS_BY_TOKEN = AssessmentFixtures.authenticationsWith(Map.of(
			"shopper", new Authentication(SHOPPER, new CatalogueIdImpl(CATALOGUE_GR)),
			"shopper-without-catalogue", new Authentication(SHOPPER, null)));

	@Inject
	DataSource dataSource;

	@Inject
	ArchetypeCorpus archetypeCorpus;

	/**
	 * An archetype of the seed data.
	 */
	private String archetype;

	private AssessmentFixtures fixtures;

	@BeforeEach
	void setUp() throws SQLException {
		QuarkusMock.installMockForType(new StubTokenHelper(AUTHENTICATIONS_BY_TOKEN), TokenHelper.class);
		QuarkusMock.installMockForType(new UnreachableProductAssessmentAiFacade(), ProductAssessmentAiFacade.class);
		fixtures = new AssessmentFixtures(dataSource);
		fixtures.reset();
		archetype = fixtures.rows("SELECT id::text FROM TH_ARCHETYPE_PRODUCT ORDER BY id LIMIT 1").getFirst();
	}

	@Test
	void aClassifiedProductIsAssessedWithItsArchetypeAndHowItWasIdentified() throws SQLException {
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", archetype);

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
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));

		assessSingle("shopper", single("soap", "el"), 200)
				.body("productKey", equalTo("soap"))
				.body("type", equalTo("FAILURE_TO_IDENTIFY"))
				.body("failureReason", equalTo("UNKNOWN_PRODUCT"))
				.body("diagnostics", equalTo(null))
				.body("bestPersonalAlternative", equalTo(null));
	}

	@Test
	void aProductThatWasNeverClassifiedIsNotClassified() {
		fixtures.applyProduct("honey", 1, "h1", listing(CATALOGUE_GR, true));

		assessSingle("shopper", single("honey", "el"), 200)
				.body("type", equalTo("FAILURE_TO_IDENTIFY"))
				.body("failureReason", equalTo("NOT_CLASSIFIED"))
				.body("diagnostics", equalTo(notClassified("honey")));
	}

	@Test
	void aProductThatNoArchetypeMatchesHasNoMatchingArchetype() {
		fixtures.applyProduct("soap", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("soap", "h1", null);

		assessSingle("shopper", single("soap", "el"), 200)
				.body("type", equalTo("FAILURE_TO_IDENTIFY"))
				.body("failureReason", equalTo("NO_MATCHING_ARCHETYPE"))
				.body("diagnostics", equalTo(noMatchingArchetype("soap")));
	}

	@Test
	void aProductIsAssessedWhetherItsListingIsInactiveOrItIsListedOnlyInAnotherCatalogue() throws SQLException {
		fixtures.applyProduct("feta", 1, "h1", listing(CATALOGUE_GR, false));
		fixtures.applyProduct("halloumi", 2, "h2", listing(CATALOGUE_CY, true));
		fixtures.classify("feta", "h1", archetype);
		fixtures.classify("halloumi", "h2", archetype);

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
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", archetype);
		fixtures.applyProduct("yoghurt", 2, "h2", listing(CATALOGUE_GR, true));

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
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, false));
		fixtures.classify("yoghurt", "h1", archetypes.reference().asString());

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
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("kefir", 2, "h2", listing(CATALOGUE_GR, true, "2.0000"));
		fixtures.applyProduct("kefir-organic", 3, "h3", listing(CATALOGUE_GR, true, "3.4900"));
		fixtures.applyProduct("kefir-on-offer", 4, "h4", listing(CATALOGUE_GR, false, "0.9900"));
		fixtures.applyProduct("kefir-in-cyprus", 5, "h5", listing(CATALOGUE_CY, true, "0.8900"));
		fixtures.classify("yoghurt", "h1", archetypes.reference().asString());
		for (var product : Map.of("kefir", "h2", "kefir-organic", "h3", "kefir-on-offer", "h4", "kefir-in-cyprus", "h5").entrySet()) {
			fixtures.classify(product.getKey(), product.getValue(), archetypes.better().asString());
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
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("kefir", 2, "h2", Map.of("el", "Κεφίρ", "en", "Kefir"), listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", archetypes.reference().asString());
		fixtures.classify("kefir", "h2", archetypes.better().asString());

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
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("kefir", 2, "h2", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", archetypes.reference().asString());
		fixtures.classify("kefir", "h2", archetypes.better().asString());

		assessSingle("shopper", single("yoghurt", "el"), 200)
				.body("bestScientificAlternative.type", equalTo("SUGGESTION"))
				.body("bestScientificAlternative.archetype.id", equalTo(archetypes.better().asString()));

		fixtures.applyProduct("kefir", 3, "h2", listing(CATALOGUE_GR, false));

		assessSingle("shopper", single("yoghurt", "el"), 200)
				.body("bestScientificAlternative.type", equalTo("GOOD_ENOUGH"))
				.body("bestScientificAlternative.archetype.id", equalTo(archetypes.reference().asString()));
	}

	@Test
	void anArchetypeListedOnlyInAnotherCatalogueIsNeverSuggested() throws SQLException {
		var archetypes = anArchetypeAndOneThatWouldRankFirstInItsPlace();
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("kefir", 2, "h2", listing(CATALOGUE_CY, true));
		fixtures.classify("yoghurt", "h1", archetypes.reference().asString());
		fixtures.classify("kefir", "h2", archetypes.better().asString());

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
		Set<ArchetypeProductId> all = fixtures.rows("SELECT id::text FROM TH_ARCHETYPE_PRODUCT").stream()
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
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("honey", 2, "h2", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("soap", 3, "h3", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("feta", 4, "h4", listing(CATALOGUE_GR, false));
		fixtures.classify("yoghurt", "h1", archetype);
		fixtures.classify("soap", "h3", null);
		fixtures.classify("feta", "h4", archetype);
	}

	private Map<String, Object> classified(String productKey, boolean pending) throws SQLException {
		var diagnostics = noMatchingArchetype(productKey);
		diagnostics.put("pending", pending);
		diagnostics.put("archetypeProductId", archetype);
		diagnostics.put("archetypeProductName", fixtures.rows("SELECT name FROM TH_ARCHETYPE_PRODUCT WHERE id = '" + archetype + "'").getFirst());
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
		diagnostics.put("retailerProductId", fixtures.productId(productKey));
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
}
