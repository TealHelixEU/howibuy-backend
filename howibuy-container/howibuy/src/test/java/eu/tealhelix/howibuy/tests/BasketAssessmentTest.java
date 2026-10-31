package eu.tealhelix.howibuy.tests;

import static eu.tealhelix.howibuy.tests.AssessmentFixtures.BASE;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.CATALOGUE_GR;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.as;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.listing;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import eu.tealhelix.howibuy.services.v1.basket.BasketRules;
import eu.tealhelix.howibuy.services.v1.impl.ArchetypeCorpus;
import eu.tealhelix.howibuy.services.v1.impl.PersonalWeightsProvider;
import eu.tealhelix.howibuy.services.v1.impl.ScoredArchetypes;
import eu.tealhelix.howibuy.v1.types.ProductGrade;
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
 * Assessing the basket a user is about to buy in a Catalogue, against a real database, with the products brought in
 * and classified as in {@link ProductAssessmentTest}. The archetypes are picked from the seed data by the Product Grade
 * their Overall Score earns, so the expectations hold whatever the seed scores are. Nothing is stored before the
 * purchase, so the user's XP total is answered as it stands.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, initArgs = @ResourceArg(name = "contexts", value = "appdata"))
public class BasketAssessmentTest {
	private static final String SHOPPER_ID = "c4a7e1d2-5b3f-4e8a-9d6c-2f1b8a7e3c66";
	private static final User SHOPPER = new UserImpl(new UserIdImpl(SHOPPER_ID), "basket-shopper", null, false, true, Set.of());
	private static final int TOTAL_XP = 12;

	private static final Map<String, Authentication> AUTHENTICATIONS_BY_TOKEN = AssessmentFixtures.authenticationsWith(Map.of(
			"shopper", new Authentication(SHOPPER, new CatalogueIdImpl(CATALOGUE_GR)),
			"shopper-without-catalogue", new Authentication(SHOPPER, null)));

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
			s.execute("INSERT INTO TH_USER_PROFILE (id) VALUES ('" + SHOPPER_ID + "') ON CONFLICT (id) DO NOTHING");
			s.execute("UPDATE TH_USER_PROFILE SET xp_total = " + TOTAL_XP + " WHERE id = '" + SHOPPER_ID + "'");
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

	private static Map<String, Object> request(String language, String... productKeys) {
		var request = new HashMap<String, Object>();
		request.put("language", language);
		request.put("productKeys", Arrays.asList(productKeys));
		return request;
	}
}
