package eu.tealhelix.howibuy.services.v1.basket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.howibuy.services.v1.basket.types.ScoredBasketProduct;
import eu.tealhelix.howibuy.v1.types.BasketMatch;
import eu.tealhelix.howibuy.v1.types.StreakEffect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class BasketRulesTest {
	private final BasketRules sut = new BasketRules();

	/**
	 * 10 XP for every product above 0.8 on the scientific Overall Score; a bonus for a basket of 5 products or more on
	 * their average: 150 above 0.8, otherwise 50 above 0.6.
	 */
	@ParameterizedTest
	@CsvSource({
			"0.9,                          10",
			"0.9 0.85 1.0,                 30",
			"0.8,                          0",
			"0.8001,                       10",
			"0.9 0.5 0.1,                  10",
			"0.9 0.9 0.9 0.9,              40",
			"0.9 0.9 0.9 0.9 0.9,          200",
			"0.9 0.9 0.9 0.9 0.9 0.9,      210",
			"0.8 0.8 0.8 0.8 0.8,          50",
			"0.8 0.8 0.8 0.8 0.81,         160",
			"0.7 0.7 0.7 0.7 0.7,          50",
			"0.6 0.6 0.6 0.6 0.61,         50",
			"0.6 0.6 0.6 0.6 0.6,          0",
			"0.1 0.1 0.1 0.1 0.9,          10",
	})
	void countsTheXpOnTheScientificOverallScores(String scientificScores, int xpValue) {
		var verdict = sut.judge(products(scientificScores, scientificScores));

		assertEquals(xpValue, verdict.xpValue());
	}

	/**
	 * The XP of a product, which the explanations of XP quote, is what every product above 0.8 earns.
	 */
	@Test
	void tellsTheXpOfAProductAbove08() {
		assertEquals(10, sut.xpPerProduct());
		assertEquals(sut.xpPerProduct(), sut.judge(products("0.9", "0.9")).xpValue());
	}

	/**
	 * WP5.3's own example: four of the ten products are above 0.8, and the average, 0.7096, earns the bonus of 50.
	 */
	@Test
	void countsTheXpOfTheSpreadsheetsExample() {
		var scores = "0.979980707166845 0.273794244601 0.749770266076726 0.909381114899094 0.62459833995715 "
				+ "0.658515573444208 0.771485302265451 0.99 0.148262799383649 0.99";

		var verdict = sut.judge(products(scores, scores));

		assertEquals(4 * 10 + 50, verdict.xpValue());
	}

	@ParameterizedTest
	@CsvSource({
			"1.0,              EXCELLENT",
			"0.8001,           EXCELLENT",
			"0.8,              GOOD",
			"0.7,              GOOD",
			"0.6001,           GOOD",
			"0.6,              NEEDS_IMPROVEMENT",
			"0.0,              NEEDS_IMPROVEMENT",
			"1.0 0.7,          EXCELLENT",
			"0.9 0.5,          GOOD",
			"0.9 0.1,          NEEDS_IMPROVEMENT",
			"0.9 0.9 0.9 0.9 0.1 0.1, GOOD",
	})
	void matchesTheBasketOnTheAveragePersonalOverallScore(String personalScores, BasketMatch basketMatch) {
		var verdict = sut.judge(products(personalScores, personalScores));

		assertEquals(basketMatch, verdict.basketMatch());
	}

	/**
	 * A basket with a product above 0.8 on the scientific Overall Score extends the Streak; one whose products are all at
	 * 0.8 or less resets it.
	 */
	@ParameterizedTest
	@CsvSource({
			"0.9,              EXTEND",
			"0.8001,           EXTEND",
			"0.1 0.2 0.9,      EXTEND",
			"0.8,              RESET",
			"0.1 0.5 0.8,      RESET",
	})
	void movesTheStreakOnTheScientificOverallScores(String scientificScores, StreakEffect streakEffect) {
		var verdict = sut.judge(products(scientificScores, scientificScores));

		assertEquals(streakEffect, verdict.streakEffect());
	}

	@Test
	void judgesTheMatchOnThePersonalOverallScoresAndTheRestOnTheScientificOnes() {
		var sustainableButNotForTheUser = sut.judge(products("0.9 0.9", "0.1 0.1"));
		var forTheUserButNotSustainable = sut.judge(products("0.1 0.1", "0.9 0.9"));

		assertEquals(BasketMatch.NEEDS_IMPROVEMENT, sustainableButNotForTheUser.basketMatch());
		assertEquals(20, sustainableButNotForTheUser.xpValue());
		assertEquals(List.of(), sustainableButNotForTheUser.problematicProductKeys());
		assertEquals(StreakEffect.EXTEND, sustainableButNotForTheUser.streakEffect());
		assertEquals(BasketMatch.EXCELLENT, forTheUserButNotSustainable.basketMatch());
		assertEquals(0, forTheUserButNotSustainable.xpValue());
		assertEquals(List.of(key(0), key(1)), forTheUserButNotSustainable.problematicProductKeys());
		assertEquals(StreakEffect.RESET, forTheUserButNotSustainable.streakEffect());
	}

	@Test
	void reportsTheProductsAtOrBelow08LowestScientificOverallScoreFirst() {
		var verdict = sut.judge(products("0.7 0.9 0.1 0.8 0.85 0.3", "0.1 0.2 0.9 0.3 0.4 0.5"));

		assertEquals(List.of(key(2), key(5), key(0), key(3)), verdict.problematicProductKeys());
	}

	@Test
	void keepsTheOrderOfTheBasketForProblematicProductsOfEqualScientificOverallScore() {
		var verdict = sut.judge(products("0.7 0.5 0.7", "0.7 0.5 0.7"));

		assertEquals(List.of(key(1), key(0), key(2)), verdict.problematicProductKeys());
	}

	@Test
	void judgesAnEmptyBasketWithoutAMatch() {
		var verdict = sut.judge(List.of());

		assertNull(verdict.basketMatch());
		assertEquals(0, verdict.xpValue());
		assertEquals(List.of(), verdict.problematicProductKeys());
		assertEquals(StreakEffect.NONE, verdict.streakEffect());
	}

	/**
	 * One product per pair of space-separated Overall Scores, keyed by its position. The basket rules do not read the
	 * Normalised Scores, so the products have none.
	 */
	private static List<ScoredBasketProduct> products(String scientificScores, String personalScores) {
		var scientific = scientificScores.trim().split(" +");
		var personal = personalScores.trim().split(" +");
		assertEquals(scientific.length, personal.length, "a personal score for every scientific one");
		return IntStream.range(0, scientific.length)
				.mapToObj(position -> new ScoredBasketProduct(key(position), Double.parseDouble(scientific[position]), Double.parseDouble(personal[position]), Map.of()))
				.toList();
	}

	private static ProductKey key(int position) {
		return new ProductKeyImpl("product-" + position);
	}
}
