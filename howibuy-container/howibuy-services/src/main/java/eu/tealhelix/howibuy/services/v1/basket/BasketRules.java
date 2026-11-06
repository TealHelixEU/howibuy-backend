package eu.tealhelix.howibuy.services.v1.basket;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.howibuy.services.v1.basket.types.BasketVerdict;
import eu.tealhelix.howibuy.services.v1.basket.types.ScoredBasketProduct;
import eu.tealhelix.howibuy.v1.types.BasketMatch;
import eu.tealhelix.howibuy.v1.types.StreakEffect;

/**
 * The rules that judge a basket from the two Overall Scores of each of its scored products: the scientific one decides
 * the XP the basket earns, its Problematic Products and its {@link StreakEffect}, the personal one its
 * {@link BasketMatch}.
 * <p>
 * The rules and their numbers are WP5.3's, from {@code TH WP5.3 GAMIFICATION FORMULAS.xlsx}. Every comparison is strict:
 * a score exactly at a threshold is not above it. The thresholds of the averages are kept apart from each other and
 * from the one of a product, even where their values coincide, since WP5.3 may set each on its own.
 */
public final class BasketRules {
	/**
	 * The scientific Overall Score above which a product earns XP and extends the Streak; a product at or below it is a
	 * Problematic Product.
	 */
	private static final double PROBLEMATIC_PRODUCT_THRESHOLD = 0.8;
	/**
	 * The XP of every product that is not a Problematic Product.
	 */
	private static final int PRODUCT_XP = 10;
	/**
	 * The fewest scored products of a basket that earns a bonus.
	 */
	private static final int BONUS_MIN_PRODUCTS = 5;
	/**
	 * The average scientific Overall Score above which a basket earns the {@link #HIGH_BONUS_XP}.
	 */
	private static final double HIGH_BONUS_THRESHOLD = 0.8;
	private static final int HIGH_BONUS_XP = 150;
	/**
	 * The average scientific Overall Score above which a basket that does not earn the {@link #HIGH_BONUS_XP} earns the
	 * {@link #LOW_BONUS_XP}.
	 */
	private static final double LOW_BONUS_THRESHOLD = 0.6;
	private static final int LOW_BONUS_XP = 50;
	/**
	 * The average personal Overall Score above which a basket is an {@link BasketMatch#EXCELLENT} match.
	 */
	private static final double EXCELLENT_MATCH_THRESHOLD = 0.8;
	/**
	 * The average personal Overall Score above which a basket that is not an excellent match is a
	 * {@link BasketMatch#GOOD} one.
	 */
	private static final double GOOD_MATCH_THRESHOLD = 0.6;

	/**
	 * Judge a basket of distinct products.
	 */
	public BasketVerdict judge(List<ScoredBasketProduct> products) {
		if (products.isEmpty()) {
			return new BasketVerdict(null, 0, List.of(), StreakEffect.NONE);
		} else {
			return new BasketVerdict(
					matchOf(average(products, ScoredBasketProduct::personalOverallScore)),
					productXp(products) + bonusXp(products),
					problematicProductKeys(products),
					streakEffect(products));
		}
	}

	/**
	 * Whether a product of this scientific Overall Score is a Problematic Product.
	 */
	public boolean isProblematic(double scientificOverallScore) {
		return scientificOverallScore <= PROBLEMATIC_PRODUCT_THRESHOLD;
	}

	/**
	 * The Basket Match of a basket of this average personal Overall Score.
	 */
	public BasketMatch matchOf(double averagePersonalOverallScore) {
		if (averagePersonalOverallScore > EXCELLENT_MATCH_THRESHOLD) {
			return BasketMatch.EXCELLENT;
		} else if (averagePersonalOverallScore > GOOD_MATCH_THRESHOLD) {
			return BasketMatch.GOOD;
		} else {
			return BasketMatch.NEEDS_IMPROVEMENT;
		}
	}

	private int productXp(List<ScoredBasketProduct> products) {
		return PRODUCT_XP * (int) products.stream().filter(product -> !isProblematic(product.scientificOverallScore())).count();
	}

	private static int bonusXp(List<ScoredBasketProduct> products) {
		if (products.size() < BONUS_MIN_PRODUCTS) {
			return 0;
		}
		var averageScientificOverallScore = average(products, ScoredBasketProduct::scientificOverallScore);
		if (averageScientificOverallScore > HIGH_BONUS_THRESHOLD) {
			return HIGH_BONUS_XP;
		} else if (averageScientificOverallScore > LOW_BONUS_THRESHOLD) {
			return LOW_BONUS_XP;
		} else {
			return 0;
		}
	}

	private StreakEffect streakEffect(List<ScoredBasketProduct> products) {
		if (products.stream().anyMatch(product -> !isProblematic(product.scientificOverallScore()))) {
			return StreakEffect.EXTEND;
		} else {
			return StreakEffect.RESET;
		}
	}

	private List<ProductKey> problematicProductKeys(List<ScoredBasketProduct> products) {
		return products.stream()
				.filter(product -> isProblematic(product.scientificOverallScore()))
				.sorted(Comparator.comparingDouble(ScoredBasketProduct::scientificOverallScore))
				.map(ScoredBasketProduct::productKey)
				.toList();
	}

	private static double average(List<ScoredBasketProduct> products, ToDoubleFunction<ScoredBasketProduct> overallScore) {
		return products.stream().mapToDouble(overallScore).average().orElseThrow();
	}
}
