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
import java.util.function.Predicate;
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
import eu.tealhelix.howibuy.services.v1.basket.BadgeRules;
import eu.tealhelix.howibuy.services.v1.basket.BasketRules;
import eu.tealhelix.howibuy.services.v1.impl.ArchetypeCorpus;
import eu.tealhelix.howibuy.services.v1.impl.PersonalWeightsProvider;
import eu.tealhelix.howibuy.services.v1.impl.ScoredArchetypes;
import eu.tealhelix.howibuy.v1.types.BasketMatch;
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
 * from the seed data by what the rules make of their Overall Scores, and the expected Basket Match is worked out from
 * those scores with the rules, so the expectations hold whatever the seed scores are. Nothing is stored before the
 * purchase, so the user's XP total and Streak are answered as they stand; the Retailer registers the purchase with its
 * own token, naming the user by the correlation id, and the basket is stored with the badges it earns, its XP added and
 * its effect applied to the Streak.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, initArgs = @ResourceArg(name = "contexts", value = "appdata"))
public class BasketAssessmentTest {
	private static final String SHOPPER_ID = "c4a7e1d2-5b3f-4e8a-9d6c-2f1b8a7e3c66";
	private static final User SHOPPER = new UserImpl(new UserIdImpl(SHOPPER_ID), "basket-shopper", null, false, true, Set.of());
	private static final int TOTAL_XP = 12;
	private static final int STREAK = 3;
	private static final String SHOPPER_CORRELATION_ID = "basket-shopper";
	private static final User RETAILER = new UserImpl(new UserIdImpl(RETAILER_ID), "test-retailer", null, false, false, Set.of(Role.RETAILER));

	private static final Map<String, Authentication> AUTHENTICATIONS_BY_TOKEN = AssessmentFixtures.authenticationsWith(Map.of(
			"shopper", new Authentication(SHOPPER, new CatalogueIdImpl(CATALOGUE_GR)),
			"shopper-without-catalogue", new Authentication(SHOPPER, null),
			"retailer", new Authentication(RETAILER, null)));

	private static final BasketRules RULES = new BasketRules();
	private static final BadgeRules BADGE_RULES = new BadgeRules();
	private static final String EARLIER_BASKET_ID = "6b1d9e3a-4f27-4c85-a0e6-d3c8b5f2a917";
	private static final Map<BasketMatch, String> MATCH_LABELS = Map.of(
			BasketMatch.EXCELLENT, "Excellent match",
			BasketMatch.GOOD, "Good match",
			BasketMatch.NEEDS_IMPROVEMENT, "Needs improvement");

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
			s.execute("DELETE FROM TH_USER_BADGE");
			s.execute("DELETE FROM TH_PURCHASED_BASKET_PRODUCT");
			s.execute("DELETE FROM TH_PURCHASED_BASKET");
			s.execute("INSERT INTO TH_USER_PROFILE (id) VALUES ('" + SHOPPER_ID + "') ON CONFLICT (id) DO NOTHING");
			s.execute("UPDATE TH_USER_PROFILE SET xp_total = " + TOTAL_XP + ", streak = " + STREAK + " WHERE id = '" + SHOPPER_ID + "'");
			s.execute("INSERT INTO TH_CORREL_ID (retailer_id, correlation_id, user_id) VALUES ('" + RETAILER_ID + "', '" + SHOPPER_CORRELATION_ID + "', '" + SHOPPER_ID + "') ON CONFLICT DO NOTHING");
			s.execute("INSERT INTO TH_CONSENT (user_id, retailer_id, state) VALUES ('" + SHOPPER_ID + "', '" + RETAILER_ID + "', 'GRANTED') ON CONFLICT DO NOTHING");
		}
	}

	/**
	 * Only the yoghurt and the feta count: the unknown product, the one never classified, the one no archetype matches
	 * and the one whose archetype has no Nutri-Score cannot be judged and are left out. The user has not completed the
	 * compass, so the Basket Match is judged on the scientific Overall Scores too.
	 */
	@Test
	void aBasketIsJudgedByItsScoredProductsAlone() throws SQLException {
		var scientific = ScientificWeights.profile();
		var yoghurt = anArchetypeThatEarnsXp();
		var feta = aProblematicArchetype();
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("feta", 2, "h2", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("honey", 3, "h3", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("soap", 4, "h4", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("ouzo", 5, "h5", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", yoghurt);
		fixtures.classify("feta", "h2", feta);
		fixtures.classify("soap", "h4", null);
		fixtures.classify("ouzo", "h5", anArchetypeWithoutAnOverallScore());
		var match = matchOf(scientific, yoghurt, feta);

		assess("shopper", request("en", "yoghurt", "unknown", "honey", "feta", "soap", "ouzo"), 200)
				.contentType(JSON)
				.body("type", equalTo("ASSESSED"))
				.body("basketMatch", equalTo(match.name()))
				.body("basketMatchLabel", equalTo(MATCH_LABELS.get(match)))
				.body("intervention", equalTo(null))
				.body("problematicProductKeys", equalTo(List.of("feta")))
				.body("xpValue", equalTo(10))
				.body("totalXp", equalTo(TOTAL_XP))
				.body("streakEffect", equalTo("EXTEND"))
				.body("streak", equalTo(STREAK))
				.body("earnedBadges", equalTo(List.of()));
	}

	@Test
	void aProductSentMoreThanOnceCountsOnce() throws SQLException {
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", anArchetypeThatEarnsXp());

		assess("shopper", request("en", "yoghurt", "yoghurt", "yoghurt"), 200)
				.body("xpValue", equalTo(10));
	}

	@Test
	void aBasketWithNothingThatCanBeJudgedHasNothingToAssess() {
		fixtures.applyProduct("honey", 1, "h1", listing(CATALOGUE_GR, true));

		for (var request : List.of(request("en", "honey", "unknown"), request("en"))) {
			assess("shopper", request, 200)
					.body("type", equalTo("NOTHING_TO_ASSESS"))
					.body("basketMatch", equalTo(null))
					.body("basketMatchLabel", equalTo(null))
					.body("problematicProductKeys", equalTo(List.of()))
					.body("xpValue", equalTo(0))
					.body("totalXp", equalTo(TOTAL_XP))
					.body("streakEffect", equalTo("NONE"))
					.body("streak", equalTo(STREAK))
					.body("earnedBadges", equalTo(List.of()));
		}
	}

	/**
	 * The same product makes one Basket Match under the scientific weights before the user completes the compass, and
	 * another under the user's own weights after.
	 */
	@Test
	void aBasketIsMatchedUnderTheUsersOwnWeightsOnceTheyCompletedTheCompass() throws SQLException {
		var scientific = ScientificWeights.profile();
		completeTheCompassCaringMostAboutTheEnvironment();
		var personal = personalWeightsProvider.forUser(SHOPPER).await().atMost(Duration.ofSeconds(60)).profile();
		var archetype = anArchetypeMatchedDifferently(scientific, personal);
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", archetype);

		assess("shopper", request("en", "yoghurt"), 200)
				.body("basketMatch", equalTo(matchOf(personal, archetype).name()));
		deleteTheShoppersCompass();
		assess("shopper", request("en", "yoghurt"), 200)
				.body("basketMatch", equalTo(matchOf(scientific, archetype).name()));
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
		var yoghurt = anArchetypeThatEarnsXp();
		var feta = aProblematicArchetype();
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("feta", 2, "h2", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("honey", 3, "h3", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", yoghurt);
		fixtures.classify("feta", "h2", feta);
		var match = matchOf(scientific, yoghurt, feta);
		var purchasedAt = "2026-10-30T10:15:30+05:00";

		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, purchasedAt, "yoghurt", "unknown", "honey", "feta"), 200)
				.contentType(JSON)
				.body("type", equalTo("ASSESSED"))
				.body("basketMatch", equalTo(match.name()))
				.body("basketMatchLabel", equalTo(MATCH_LABELS.get(match)))
				.body("problematicProductKeys", equalTo(List.of("feta")))
				.body("xpValue", equalTo(10))
				.body("totalXp", equalTo(TOTAL_XP + 10))
				.body("streakEffect", equalTo("EXTEND"))
				.body("streak", equalTo(STREAK + 1))
				.body("earnedBadges", equalTo(List.of()));

		var serverTime = OffsetDateTime.parse(purchasedAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
		assertEquals(
				List.of(SHOPPER_ID + "|" + RETAILER_ID + "|" + CATALOGUE_GR + "|" + serverTime + "|ASSESSED|" + match + "|10|null"),
				fixtures.rows("SELECT user_profile_id, retailer_id, catalogue_id, to_char(purchased_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'), outcome_type, basket_match, xp_value, compass_attempt_id FROM TH_PURCHASED_BASKET"),
				"bought at the time the Retailer states, under the scientific weights");
		assertEquals(List.of(storedProduct("feta", feta, scientific), storedProduct("yoghurt", yoghurt, scientific)), storedProducts());
		assertEquals(TOTAL_XP + 10, xpTotal(SHOPPER_ID));
		assertEquals(STREAK + 1, streak(SHOPPER_ID));
	}

	@Test
	void aPurchasedBasketRecordsTheCompassAttemptItsWeightsCameFrom() throws SQLException {
		completeTheCompassCaringMostAboutTheEnvironment();
		var personal = personalWeightsProvider.forUser(SHOPPER).await().atMost(Duration.ofSeconds(60)).profile();
		var yoghurt = anArchetypeMatchedDifferently(ScientificWeights.profile(), personal);
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", yoghurt);

		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null, "yoghurt"), 200).body("basketMatch", equalTo(matchOf(personal, yoghurt).name()));

		assertEquals(
				fixtures.rows("SELECT id FROM TH_SFC_ATTEMPT WHERE user_id = '" + SHOPPER_ID + "'"),
				fixtures.rows("SELECT compass_attempt_id FROM TH_PURCHASED_BASKET"));
		assertEquals(List.of(storedProduct("yoghurt", yoghurt, personal)), storedProducts(), "scored under the user's own weights and the scientific ones");
	}

	@Test
	void twoPurchasesAddUp() throws SQLException {
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", anArchetypeThatEarnsXp());

		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null, "yoghurt"), 200).body("totalXp", equalTo(TOTAL_XP + 10));
		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null, "yoghurt"), 200).body("totalXp", equalTo(TOTAL_XP + 20));

		assertEquals(TOTAL_XP + 20, xpTotal(SHOPPER_ID));
		assertEquals(List.of("2"), fixtures.rows("SELECT count(*) FROM TH_PURCHASED_BASKET WHERE user_profile_id = '" + SHOPPER_ID + "'"),
				"every call is another purchase");
	}

	/**
	 * Each purchase with a product above 0.8 on the scientific Overall Score extends the Streak, in the order the
	 * purchases are registered; one whose products are all at 0.8 or less resets it, and one with nothing to assess
	 * leaves it as it is.
	 */
	@Test
	void successivePurchasesMoveTheStreak() throws SQLException {
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("feta", 2, "h2", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", anArchetypeThatEarnsXp());
		fixtures.classify("feta", "h2", aProblematicArchetype());

		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null, "yoghurt", "feta"), 200)
				.body("streakEffect", equalTo("EXTEND")).body("streak", equalTo(STREAK + 1));
		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null, "yoghurt"), 200)
				.body("streakEffect", equalTo("EXTEND")).body("streak", equalTo(STREAK + 2));
		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null, "unknown"), 200)
				.body("streakEffect", equalTo("NONE")).body("streak", equalTo(STREAK + 2));
		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null, "feta"), 200)
				.body("streakEffect", equalTo("RESET")).body("streak", equalTo(0));
		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null, "yoghurt"), 200)
				.body("streakEffect", equalTo("EXTEND")).body("streak", equalTo(1));

		assertEquals(1, streak(SHOPPER_ID));
	}

	/**
	 * The user has bought 49 products above 0.8 on the environment, one short of Environmental Hero Level 1, so a basket
	 * with one more earns it: the assessment before the purchase says it would, the purchase that it did, and the next
	 * basket earns it no more.
	 */
	@Test
	void theBasketThatReachesTheNumberOfABadgeEarnsIt() throws SQLException {
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", anArchetypeAboveTheBadgeThresholdOnTheEnvironment());
		theShopperHasBoughtProductsAbove08OnTheEnvironment(49);
		var environmentalHero1 = List.of(Map.of("badge", "ENVIRONMENTAL_HERO_1", "label", "Environmental Hero Level 1"));

		assess("shopper", request("en", "yoghurt"), 200).body("earnedBadges", equalTo(environmentalHero1));
		assertEquals(List.of(), fixtures.rows("SELECT badge FROM TH_USER_BADGE"), "nothing is stored before the purchase");

		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, "2026-10-30T10:15:30+05:00", "yoghurt"), 200).body("earnedBadges", equalTo(environmentalHero1));
		var basketIdAndRecordedAt = fixtures.rows("SELECT id, recorded_at FROM TH_PURCHASED_BASKET WHERE id <> '" + EARLIER_BASKET_ID + "'").getFirst();
		assertEquals(List.of(SHOPPER_ID + "|ENVIRONMENTAL_HERO_1|" + basketIdAndRecordedAt),
				fixtures.rows("SELECT user_profile_id, badge, purchased_basket_id, earned_at FROM TH_USER_BADGE"),
				"earned by the basket, when the purchase was recorded, not when it was made");

		assess("shopper", request("en", "yoghurt"), 200).body("earnedBadges", equalTo(List.of()));
		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null, "yoghurt"), 200).body("earnedBadges", equalTo(List.of()));
		assertEquals(List.of("1"), fixtures.rows("SELECT count(*) FROM TH_USER_BADGE"), "a badge is earned once");
	}

	@Test
	void aPurchaseWithNothingToAssessIsStoredWithoutProducts() throws SQLException {
		purchase("retailer", purchaseRequest(SHOPPER_CORRELATION_ID, null), 200)
				.body("type", equalTo("NOTHING_TO_ASSESS"))
				.body("basketMatch", equalTo(null))
				.body("xpValue", equalTo(0))
				.body("totalXp", equalTo(TOTAL_XP))
				.body("streakEffect", equalTo("NONE"))
				.body("streak", equalTo(STREAK))
				.body("earnedBadges", equalTo(List.of()));

		assertEquals(List.of("NOTHING_TO_ASSESS|null|0"), fixtures.rows("SELECT outcome_type, basket_match, xp_value FROM TH_PURCHASED_BASKET"));
		assertEquals(List.of(), storedProducts());
		assertEquals(TOTAL_XP, xpTotal(SHOPPER_ID));
		assertEquals(STREAK, streak(SHOPPER_ID));
	}

	@Test
	void aCorrelationIdNotSeenBeforeBringsTheUserIntoBeing() throws SQLException {
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", anArchetypeThatEarnsXp());
		var correlationId = UUID.randomUUID().toString();

		purchase("retailer", purchaseRequest(correlationId, null, "yoghurt"), 200).body("totalXp", equalTo(10)).body("streak", equalTo(1));

		var userId = fixtures.rows("SELECT user_id FROM TH_CORREL_ID WHERE retailer_id = '" + RETAILER_ID + "' AND correlation_id = '" + correlationId + "'").getFirst();
		assertEquals(List.of(userId), fixtures.rows("SELECT user_profile_id FROM TH_PURCHASED_BASKET"));
		assertEquals(10, xpTotal(userId));
		assertEquals(1, streak(userId));
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
		assertEquals(STREAK, streak(SHOPPER_ID));
	}

	/**
	 * Stores an earlier basket of the shopper, with as many products above 0.8 on the environment alone.
	 */
	private void theShopperHasBoughtProductsAbove08OnTheEnvironment(int products) throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement()) {
			s.execute("INSERT INTO TH_PURCHASED_BASKET (id, user_profile_id, retailer_id, catalogue_id, purchased_at, recorded_at, outcome_type, basket_match, xp_value) "
					+ "VALUES ('" + EARLIER_BASKET_ID + "', '" + SHOPPER_ID + "', '" + RETAILER_ID + "', '" + CATALOGUE_GR + "', now(), now(), 'ASSESSED', 'GOOD', 0)");
			s.execute("INSERT INTO TH_PURCHASED_BASKET_PRODUCT (purchased_basket_id, product_key, archetype_product_id, personal_overall_score, scientific_overall_score, "
					+ "score_environment, score_animal_welfare, score_socio_economic, score_health) "
					+ "SELECT '" + EARLIER_BASKET_ID + "', 'earlier-' || n, '" + UUID.randomUUID() + "', 0.5, 0.5, 0.9, 0.1, 0.1, 0.1 FROM generate_series(1, " + products + ") n");
		}
	}

	private int xpTotal(String userId) throws SQLException {
		return Integer.parseInt(fixtures.rows("SELECT xp_total FROM TH_USER_PROFILE WHERE id = '" + userId + "'").getFirst());
	}

	private int streak(String userId) throws SQLException {
		return Integer.parseInt(fixtures.rows("SELECT streak FROM TH_USER_PROFILE WHERE id = '" + userId + "'").getFirst());
	}

	/**
	 * @return The stored products, their scores written as Java writes a {@code double}, not as Postgres does
	 */
	private List<String> storedProducts() throws SQLException {
		return fixtures.rows("SELECT product_key, archetype_product_id, personal_overall_score, scientific_overall_score, score_environment, score_animal_welfare, score_socio_economic, score_health "
						+ "FROM TH_PURCHASED_BASKET_PRODUCT ORDER BY product_key").stream()
				.map(row -> {
					var columns = row.split("\\|");
					for (var score : new int[] {2, 3, 4, 5, 6, 7}) {
						columns[score] = String.valueOf(Double.parseDouble(columns[score]));
					}
					return String.join("|", columns);
				})
				.toList();
	}

	/**
	 * A product as it is stored: with the Overall Scores of its archetype under the profile in force and under the
	 * scientific one, and its four Normalised Scores.
	 */
	private String storedProduct(String productKey, String archetype, WeightProfile profile) {
		var scoredProduct = scoredArchetypes().find(new ArchetypeProductIdImpl(archetype)).orElseThrow();
		var scores = scoredProduct.normalisedScores();
		return String.join("|", productKey, archetype, String.valueOf(scoredProduct.overallScore(profile)),
				String.valueOf(scoredProduct.overallScore(ScientificWeights.profile())),
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

	private Optional<Double> overallScore(String archetype, WeightProfile profile) {
		return scoredArchetypes().find(new ArchetypeProductIdImpl(archetype)).map(product -> product.overallScore(profile));
	}

	/**
	 * The Basket Match of a basket of the archetypes, every one of which has an Overall Score.
	 */
	private BasketMatch matchOf(WeightProfile profile, String... archetypes) {
		var average = Arrays.stream(archetypes).mapToDouble(archetype -> overallScore(archetype, profile).orElseThrow()).average().orElseThrow();
		return RULES.matchOf(average);
	}

	private String anArchetypeThatEarnsXp() throws SQLException {
		return anArchetype(archetype -> overallScore(archetype, ScientificWeights.profile()).filter(score -> !RULES.isProblematic(score)).isPresent(),
				"earns XP");
	}

	private String anArchetypeAboveTheBadgeThresholdOnTheEnvironment() throws SQLException {
		return anArchetype(archetype -> scoredArchetypes().find(new ArchetypeProductIdImpl(archetype))
						.filter(product -> product.normalisedScores().get(ScoredSustainabilityDimension.ENVIRONMENT) > BADGE_RULES.normalisedScoreThreshold())
						.isPresent(),
				"is above the threshold of the badges on the environment");
	}

	private String aProblematicArchetype() throws SQLException {
		return anArchetype(archetype -> overallScore(archetype, ScientificWeights.profile()).filter(RULES::isProblematic).isPresent(),
				"is a Problematic Product");
	}

	private String anArchetypeWithoutAnOverallScore() throws SQLException {
		return anArchetype(archetype -> overallScore(archetype, ScientificWeights.profile()).isEmpty(), "is without an Overall Score");
	}

	private String anArchetypeMatchedDifferently(WeightProfile scientific, WeightProfile personal) throws SQLException {
		assertNotEquals(scientific.getDimensionWeights(), personal.getDimensionWeights(), "the compass gave the user weights of their own");
		return anArchetype(archetype -> overallScore(archetype, scientific).isPresent() && matchOf(scientific, archetype) != matchOf(personal, archetype),
				"is matched differently under the user's weights");
	}

	private String anArchetype(Predicate<String> condition, String description) throws SQLException {
		return seedArchetypes().stream()
				.filter(condition)
				.findFirst()
				.orElseThrow(() -> new AssertionError("No seed archetype " + description));
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
