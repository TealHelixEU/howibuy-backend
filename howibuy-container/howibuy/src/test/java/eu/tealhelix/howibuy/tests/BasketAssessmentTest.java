package eu.tealhelix.howibuy.tests;

import static eu.tealhelix.howibuy.tests.AssessmentFixtures.BASE;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.CATALOGUE_GR;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.RETAILER_ID;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.as;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.listing;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.services.v1.ai.ProductAssessmentAiFacade;
import eu.tealhelix.howibuy.services.v1.basket.BasketRules;
import eu.tealhelix.howibuy.services.v1.impl.ArchetypeCorpus;
import eu.tealhelix.howibuy.services.v1.impl.PersonalWeightsProvider;
import eu.tealhelix.howibuy.services.v1.impl.ScoredArchetypes;
import eu.tealhelix.howibuy.v1.types.ProductGrade;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Assessing the basket a user is about to buy in a Catalogue, and registering the one they bought, against a real
 * database, with the products brought in and classified as in {@link ProductAssessmentTest}. The archetypes are picked
 * from the seed data by the Product Grade their Overall Score earns, so the expectations hold whatever the seed scores
 * are. Nothing is stored before the purchase, so the user's XP total is answered as it stands; the Retailer registers
 * the purchase with its own token, naming the user by the correlation id, and the basket is stored and its XP added.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, initArgs = @ResourceArg(name = "contexts", value = "appdata"))
public class BasketAssessmentTest {
	private static final String SHOPPER_ID = "c4a7e1d2-5b3f-4e8a-9d6c-2f1b8a7e3c66";
	private static final User SHOPPER = new UserImpl(new UserIdImpl(SHOPPER_ID), "basket-shopper", null, false, true, Set.of());
	private static final int TOTAL_XP = 12;
	private static final String SHOPPER_CORRELATION_ID = "basket-shopper";
	private static final User RETAILER = new UserImpl(new UserIdImpl(RETAILER_ID), "test-retailer", null, false, false, Set.of(Role.RETAILER));

	private static final Map<String, Authentication> AUTHENTICATIONS_BY_TOKEN = AssessmentFixtures.authenticationsWith(Map.of(
			"shopper", new Authentication(SHOPPER, new CatalogueIdImpl(CATALOGUE_GR)),
			"shopper-without-catalogue", new Authentication(SHOPPER, null),
			"retailer", new Authentication(RETAILER, null)));

	private static final BasketRules RULES = new BasketRules();

	@Inject
	DataSource dataSource;

	@Inject
	ArchetypeCorpus archetypeCorpus;

	@Inject
	PersonalWeightsProvider personalWeightsProvider;

	private AssessmentFixtures fixtures;

	@BeforeEach
	void setUp() throws SQLException {
		QuarkusMock.installMockForType(new StubTokenHelper(AUTHENTICATIONS_BY_TOKEN), TokenHelper.class);
		QuarkusMock.installMockForType(new UnreachableProductAssessmentAiFacade(), ProductAssessmentAiFacade.class);
		fixtures = new AssessmentFixtures(dataSource);
		fixtures.reset();
		deleteTheShoppersCompass();
		try (var c = dataSource.getConnection(); var s = c.createStatement()) {
			s.execute("DELETE FROM TH_PURCHASED_BASKET_PRODUCT");
			s.execute("DELETE FROM TH_PURCHASED_BASKET");
			s.execute("INSERT INTO TH_USER_PROFILE (id) VALUES ('" + SHOPPER_ID + "') ON CONFLICT (id) DO NOTHING");
			s.execute("UPDATE TH_USER_PROFILE SET xp_total = " + TOTAL_XP + " WHERE id = '" + SHOPPER_ID + "'");
			s.execute("INSERT INTO TH_CORREL_ID (retailer_id, correlation_id, user_id) VALUES ('" + RETAILER_ID + "', '" + SHOPPER_CORRELATION_ID + "', '" + SHOPPER_ID + "') ON CONFLICT DO NOTHING");
			s.execute("INSERT INTO TH_CONSENT (user_id, retailer_id, state) VALUES ('" + SHOPPER_ID + "', '" + RETAILER_ID + "', 'GRANTED') ON CONFLICT DO NOTHING");
		}
	}

	/**
	 * Only the product graded C counts against the user: the unknown product, the one never classified, the one no
	 * archetype matches and the one whose archetype has no Nutri-Score cannot be judged and are left out.
	 */
	@Test
	void aBasketIsGradedByItsScoredProductsAlone() throws SQLException {
		var scientific = ScientificWeights.profile();
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("feta", 2, "h2", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("honey", 3, "h3", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("soap", 4, "h4", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("ouzo", 5, "h5", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", anArchetypeGraded(ProductGrade.A, scientific));
		fixtures.classify("feta", "h2", anArchetypeGraded(ProductGrade.C, scientific));
		fixtures.classify("soap", "h4", null);
		fixtures.classify("ouzo", "h5", anArchetypeWithoutAnOverallScore());

		assess("shopper", request("en", "yoghurt", "unknown", "honey", "feta", "soap", "ouzo"), 200)
				.contentType(JSON)
				.body("type", equalTo("ASSESSED"))
				.body("basketGrade", equalTo("C"))
				.body("basketGradeLabel", equalTo("Basket to improve"))
				.body("intervention", equalTo(null))
				.body("problematicProductKeys", equalTo(List.of("feta")))
				.body("xpValue", equalTo(3))
				.body("totalXp", equalTo(TOTAL_XP));
	}

	@Test
	void aProductSentMoreThanOnceCountsOnce() throws SQLException {
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", anArchetypeGraded(ProductGrade.A, ScientificWeights.profile()));

		assess("shopper", request("en", "yoghurt", "yoghurt", "yoghurt"), 200)
				.body("basketGrade", equalTo("A"))
				.body("xpValue", equalTo(3));
	}

	@Test
	void aBasketWithNothingThatCanBeJudgedHasNothingToAssess() {
		fixtures.applyProduct("honey", 1, "h1", listing(CATALOGUE_GR, true));

		for (var request : List.of(request("en", "honey", "unknown"), request("en"))) {
			assess("shopper", request, 200)
					.body("type", equalTo("NOTHING_TO_ASSESS"))
					.body("basketGrade", equalTo(null))
					.body("basketGradeLabel", equalTo(null))
					.body("problematicProductKeys", equalTo(List.of()))
					.body("xpValue", equalTo(0))
					.body("totalXp", equalTo(TOTAL_XP));
		}
	}

	/**
	 * The same product earns one grade under the scientific weights before the user completes the compass, and another
	 * under the user's own weights after.
	 */
	@Test
	void aBasketIsGradedUnderTheUsersOwnWeightsOnceTheyCompletedTheCompass() throws SQLException {
		var scientific = ScientificWeights.profile();
		completeTheCompassCaringMostAboutTheEnvironment();
		var personal = personalWeightsProvider.forUser(SHOPPER).await().atMost(Duration.ofSeconds(60)).profile();
		var archetype = anArchetypeGradedDifferently(scientific, personal);
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", archetype);

		assess("shopper", request("en", "yoghurt"), 200)
				.body("basketGrade", equalTo(gradeOf(archetype, personal).orElseThrow().name()));
		deleteTheShoppersCompass();
		assess("shopper", request("en", "yoghurt"), 200)
				.body("basketGrade", equalTo(gradeOf(archetype, scientific).orElseThrow().name()));
	}

	@Test
	void aBasketOfTheMostProductsIsAssessedAndOneMoreIsRefused() {
		var keys = new ArrayList<>(Collections.nCopies(400, "unknown"));

		assess("shopper", request("en", keys.toArray(String[]::new)), 200).body("type", equalTo("NOTHING_TO_ASSESS"));
		keys.add("unknown");
		assess("shopper", request("en", keys.toArray(String[]::new)), 400)
				.body("message", equalTo("The value of input 'productKeys' is illegal."));
	}

	@Test
	void aBasketWithoutALanguageOrAProductKeyIsRefused() {
		assess("shopper", request(null, "yoghurt"), 400).body("message", equalTo("Required input 'language' is missing."));
		assess("shopper", request("en", "yoghurt", null), 400).body("message", equalTo("Required input 'productKeys' is missing."));
		assess("shopper", Map.of("language", "en"), 400).body("message", equalTo("Required input 'productKeys' is missing."));
	}

	@Test
	void aBasketWhoseTokenNamesNoCatalogueIsForbidden() {
		assess("shopper-without-catalogue", request("en", "yoghurt"), 403);
	}

	/**
	 * The scored products are stored with the scores of their archetypes; the unknown product and the one never
	 * classified are left out, as from the assessment.
	 */
	@Test
	void aPurchasedBasketIsStoredWithTheScoresOfItsProducts() throws SQLException {
		var scientific = ScientificWeights.profile();
		var yoghurt = anArchetypeGraded(ProductGrade.A, scientific);
		var feta = anArchetypeGraded(ProductGrade.C, scientific);
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("feta", 2, "h2", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("honey", 3, "h3", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", yoghurt);
		fixtures.classify("feta", "h2", feta);
		var purchasedAt = "2026-10-30T10:15:30+05:00";

		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, purchasedAt, "yoghurt", "unknown", "honey", "feta"), 200)
				.contentType(JSON)
				.body("type", equalTo("ASSESSED"))
				.body("basketGrade", equalTo("C"))
				.body("basketGradeLabel", equalTo("Basket to improve"))
				.body("problematicProductKeys", equalTo(List.of("feta")))
				.body("xpValue", equalTo(3))
				.body("totalXp", equalTo(TOTAL_XP + 3));

		var serverTime = OffsetDateTime.parse(purchasedAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
		assertEquals(
				List.of(SHOPPER_ID + "|" + RETAILER_ID + "|" + CATALOGUE_GR + "|" + serverTime + "|ASSESSED|C|3|null"),
				fixtures.rows("SELECT user_profile_id, retailer_id, catalogue_id, to_char(purchased_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'), outcome_type, basket_grade, xp_value, compass_attempt_id FROM TH_PURCHASED_BASKET"),
				"bought at the time the Retailer states, under the scientific weights");
		assertEquals(List.of(storedProduct("feta", feta, "C", scientific), storedProduct("yoghurt", yoghurt, "A", scientific)), storedProducts());
		assertEquals(TOTAL_XP + 3, xpTotal(SHOPPER_ID));
	}

	@Test
	void aPurchasedBasketRecordsTheCompassAttemptItsWeightsCameFrom() throws SQLException {
		completeTheCompassCaringMostAboutTheEnvironment();
		var personal = personalWeightsProvider.forUser(SHOPPER).await().atMost(Duration.ofSeconds(60)).profile();
		var yoghurt = anArchetypeGradedDifferently(ScientificWeights.profile(), personal);
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", yoghurt);
		var grade = gradeOf(yoghurt, personal).orElseThrow().name();

		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null, "yoghurt"), 200).body("basketGrade", equalTo(grade));

		assertEquals(
				fixtures.rows("SELECT id FROM TH_SFC_ATTEMPT WHERE user_id = '" + SHOPPER_ID + "'"),
				fixtures.rows("SELECT compass_attempt_id FROM TH_PURCHASED_BASKET"));
		assertEquals(List.of(storedProduct("yoghurt", yoghurt, grade, personal)), storedProducts(), "scored under the user's own weights");
	}

	@Test
	void twoPurchasesAddUp() throws SQLException {
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", anArchetypeGraded(ProductGrade.A, ScientificWeights.profile()));

		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null, "yoghurt"), 200).body("totalXp", equalTo(TOTAL_XP + 3));
		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null, "yoghurt"), 200).body("totalXp", equalTo(TOTAL_XP + 6));

		assertEquals(TOTAL_XP + 6, xpTotal(SHOPPER_ID));
		assertEquals(List.of("2"), fixtures.rows("SELECT count(*) FROM TH_PURCHASED_BASKET WHERE user_profile_id = '" + SHOPPER_ID + "'"),
				"every call is another purchase");
	}

	@Test
	void aPurchaseWithNothingToAssessIsStoredWithoutProducts() throws SQLException {
		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null), 200)
				.body("type", equalTo("NOTHING_TO_ASSESS"))
				.body("basketGrade", equalTo(null))
				.body("xpValue", equalTo(0))
				.body("totalXp", equalTo(TOTAL_XP));

		assertEquals(List.of("NOTHING_TO_ASSESS|null|0"), fixtures.rows("SELECT outcome_type, basket_grade, xp_value FROM TH_PURCHASED_BASKET"));
		assertEquals(List.of(), storedProducts());
		assertEquals(TOTAL_XP, xpTotal(SHOPPER_ID));
	}

	@Test
	void aCorrelationIdNotSeenBeforeBringsTheUserIntoBeing() throws SQLException {
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", anArchetypeGraded(ProductGrade.A, ScientificWeights.profile()));
		var correlationId = UUID.randomUUID().toString();

		purchase("retailer", purchaseRequest(correlationId, null, "yoghurt"), 200).body("totalXp", equalTo(3));

		var userId = fixtures.rows("SELECT user_id FROM TH_CORREL_ID WHERE retailer_id = '" + RETAILER_ID + "' AND correlation_id = '" + correlationId + "'").getFirst();
		assertEquals(List.of(userId), fixtures.rows("SELECT user_profile_id FROM TH_PURCHASED_BASKET"));
		assertEquals(3, xpTotal(userId));
	}

	@Test
	void thePurchaseTimeIsTheTimeOfTheRequestWhenNotGiven() throws SQLException {
		var before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null), 200);

		var after = LocalDateTime.now().plusSeconds(1);
		var times = fixtures.rows("SELECT purchased_at, recorded_at FROM TH_PURCHASED_BASKET").getFirst().split("\\|");
		assertEquals(times[1], times[0], "bought when it was registered");
		var recordedAt = LocalDateTime.parse(times[1].replace(' ', 'T'));
		assertFalse(recordedAt.isBefore(before), "recorded at " + recordedAt + ", not before " + before);
		assertTrue(recordedAt.isBefore(after), "recorded at " + recordedAt + ", before " + after);
	}

	@Test
	void aPurchaseTimeInTheFutureIsRefused() throws SQLException {
		var inAnHour = OffsetDateTime.now().plusHours(1).toString();

		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, inAnHour), 400)
				.body("message", equalTo("The value of input 'purchasedAt' is illegal."));

		assertNothingPurchased();
	}

	@Test
	void aPurchaseWithoutACatalogueOrACorrelationIdIsRefused() throws SQLException {
		var withoutCatalogue = purchaseRequest(SHOPPER_CORRELATION_ID, null);
		withoutCatalogue.remove("catalogueId");
		purchase("retailer", withoutCatalogue, 400).body("message", equalTo("Required input 'catalogueId' is missing."));
		purchase("retailer", purchaseRequest(null, null), 400).body("message", equalTo("Required input 'correlationId' is missing."));
		purchase("retailer", Map.of("correlationId", SHOPPER_CORRELATION_ID, "catalogueId", CATALOGUE_GR, "language", "en"), 400)
				.body("message", equalTo("Required input 'productKeys' is missing."));

		assertNothingPurchased();
	}

	/**
	 * A purchase is registered by the Retailer's backend alone: neither the user's own token nor a service account
	 * without the Retailer role may register one.
	 */
	@Test
	void onlyARetailerMayRegisterAPurchase() throws SQLException {
		purchase("shopper", purchaseRequest(SHOPPER_CORRELATION_ID, null), 403);
		purchase("sync", purchaseRequest(SHOPPER_CORRELATION_ID, null), 403);

		assertNothingPurchased();
	}

	private void assertNothingPurchased() throws SQLException {
		assertEquals(List.of(), fixtures.rows("SELECT id FROM TH_PURCHASED_BASKET"));
		assertEquals(TOTAL_XP, xpTotal(SHOPPER_ID));
	}

	private int xpTotal(String userId) throws SQLException {
		return Integer.parseInt(fixtures.rows("SELECT xp_total FROM TH_USER_PROFILE WHERE id = '" + userId + "'").getFirst());
	}

	/**
	 * @return The stored products, their scores written as Java writes a {@code double}, not as Postgres does
	 */
	private List<String> storedProducts() throws SQLException {
		return fixtures.rows("SELECT product_key, archetype_product_id, overall_score, product_grade, score_environment, score_animal_welfare, score_socio_economic, score_health "
						+ "FROM TH_PURCHASED_BASKET_PRODUCT ORDER BY product_key").stream()
				.map(row -> {
					var columns = row.split("\\|");
					for (var score : new int[] {2, 4, 5, 6, 7}) {
						columns[score] = String.valueOf(Double.parseDouble(columns[score]));
					}
					return String.join("|", columns);
				})
				.toList();
	}

	/**
	 * A product as it is stored: with the Overall Score of its archetype under the profile in force and its four
	 * Normalised Scores.
	 */
	private String storedProduct(String productKey, String archetype, String productGrade, WeightProfile profile) {
		var scoredProduct = scoredArchetypes().find(new ArchetypeProductIdImpl(archetype)).orElseThrow();
		var scores = scoredProduct.normalisedScores();
		return String.join("|", productKey, archetype, String.valueOf(scoredProduct.overallScore(profile)), productGrade,
				String.valueOf(scores.get(ScoredSustainabilityDimension.ENVIRONMENT)),
				String.valueOf(scores.get(ScoredSustainabilityDimension.ANIMAL_WELFARE)),
				String.valueOf(scores.get(ScoredSustainabilityDimension.SOCIO_ECONOMIC)),
				String.valueOf(scores.get(ScoredSustainabilityDimension.HEALTH)));
	}

	private ScoredArchetypes scoredArchetypes() {
		return archetypeCorpus.scoredArchetypes().await().atMost(Duration.ofSeconds(60));
	}

	private List<String> seedArchetypes() throws SQLException {
		return fixtures.rows("SELECT id::text FROM TH_ARCHETYPE_PRODUCT ORDER BY id");
	}

	private Optional<ProductGrade> gradeOf(String archetype, WeightProfile profile) {
		return scoredArchetypes().find(new ArchetypeProductIdImpl(archetype)).map(product -> RULES.gradeOf(product.overallScore(profile)));
	}

	private String anArchetypeGraded(ProductGrade grade, WeightProfile profile) throws SQLException {
		return seedArchetypes().stream()
				.filter(archetype -> gradeOf(archetype, profile).equals(Optional.of(grade)))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No seed archetype is graded " + grade));
	}

	private String anArchetypeWithoutAnOverallScore() throws SQLException {
		return seedArchetypes().stream()
				.filter(archetype -> gradeOf(archetype, ScientificWeights.profile()).isEmpty())
				.findFirst()
				.orElseThrow(() -> new AssertionError("Every seed archetype has an Overall Score"));
	}

	private String anArchetypeGradedDifferently(WeightProfile scientific, WeightProfile personal) throws SQLException {
		assertNotEquals(scientific.getDimensionWeights(), personal.getDimensionWeights(), "the compass gave the user weights of their own");
		return seedArchetypes().stream()
				.filter(archetype -> gradeOf(archetype, scientific).isPresent() && !gradeOf(archetype, scientific).equals(gradeOf(archetype, personal)))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No seed archetype is graded differently under the user's weights"));
	}

	/**
	 * Answers every question of the ecological dimension as extremely important and every other one as not important,
	 * through the compass endpoints.
	 */
	private void completeTheCompassCaringMostAboutTheEnvironment() throws SQLException {
		var questions = fixtures.rows("SELECT q.id, c.dimension FROM TH_SFC_QUESTION q JOIN TH_SFC_CATEGORY c ON q.category_id = c.id ORDER BY q.id");
		for (var question : questions) {
			var idAndDimension = question.split("\\|");
			var option = idAndDimension[1].equals("ECOLOGICAL") ? "EXTREMELY_IMPORTANT" : "NOT_IMPORTANT";
			as("shopper").contentType(JSON).body(Map.of("option", option)).put(BASE + "/sfc/questions/" + idAndDimension[0] + "/answer")
					.then().statusCode(204);
		}
		as("shopper").post(BASE + "/sfc/attempts/current/completion").then().statusCode(204);
	}

	private void deleteTheShoppersCompass() throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement()) {
			s.execute("DELETE FROM TH_SFC_ANSWER WHERE attempt_id IN (SELECT id FROM TH_SFC_ATTEMPT WHERE user_id = '" + SHOPPER_ID + "')");
			s.execute("DELETE FROM TH_SFC_ATTEMPT WHERE user_id = '" + SHOPPER_ID + "'");
		}
	}

	private static ValidatableResponse assess(String token, Map<String, Object> request, int expectedStatus) {
		return as(token).contentType(JSON).body(request).post(BASE + "/assessment/basket").then().statusCode(expectedStatus);
	}

	private static ValidatableResponse purchase(String token, Map<String, Object> request, int expectedStatus) {
		return as(token).contentType(JSON).body(request).post(BASE + "/assessment/basket/purchase").then().statusCode(expectedStatus);
	}

	private static Map<String, Object> purchaseRequest(String correlationId, String purchasedAt, String... productKeys) {
		var request = request("en", productKeys);
		request.put("correlationId", correlationId);
		request.put("catalogueId", CATALOGUE_GR);
		request.put("purchasedAt", purchasedAt);
		return request;
	}

	private static Map<String, Object> request(String language, String... productKeys) {
		var request = new HashMap<String, Object>();
		request.put("language", language);
		request.put("productKeys", Arrays.asList(productKeys));
		return request;
	}
}
