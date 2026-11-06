package eu.tealhelix.howibuy.services.v1.basket;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIO_ECONOMIC;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.tealhelix.howibuy.services.v1.basket.types.ScoredBasketProduct;
import eu.tealhelix.howibuy.v1.types.Badge;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;

/**
 * The rules that award the {@link Badge Badges}: each badge is awarded for a number of purchased products above 0.8 on
 * the Normalised Score of one dimension, 50, 100 and 200 for its levels 1, 2 and 3. Every product of every Purchased
 * Basket counts once per basket.
 * <p>
 * The rules and their numbers are WP5.3's, from {@code TH WP5.3 GAMIFICATION FORMULAS.xlsx}. The comparison is strict:
 * a score exactly at the threshold is not above it. A badge is kept once earned, whatever these rules become.
 */
public final class BadgeRules {
	/**
	 * The Normalised Score of a dimension above which a purchased product counts towards the badges of that dimension.
	 */
	private static final double NORMALISED_SCORE_THRESHOLD = 0.8;
	private static final int LEVEL_1_PRODUCTS = 50;
	private static final int LEVEL_2_PRODUCTS = 100;
	private static final int LEVEL_3_PRODUCTS = 200;

	/**
	 * What awards a badge.
	 *
	 * @param dimension the dimension whose Normalised Score a product must be above the threshold on
	 * @param products  the number of such products the user must have purchased
	 */
	private record Requirement(ScoredSustainabilityDimension dimension, int products) {
	}

	/**
	 * The Normalised Score above which a purchased product counts towards the badges of a dimension.
	 */
	public double normalisedScoreThreshold() {
		return NORMALISED_SCORE_THRESHOLD;
	}

	/**
	 * Calculate the badges a basket earns.
	 * <p>
	 * Rules:
	 * <ol>
	 *     <li>A basket without scored products earns none, even when the stored products alone reach a number</li>
	 *     <li>A badge the user already holds is not earned again</li>
	 *     <li>The user's stored products + this basket's products, above the threshold in the badge's dimension,
	 *     &ge; the badge's number of products</li>
	 * </ol>
	 *
	 * @param storedCounts the number of the user's stored products above the threshold, per dimension
	 * @param held         the badges the user holds
	 * @param products     the scored products of the basket, distinct
	 * @return The badges earned, in the order of {@link Badge}
	 */
	public List<Badge> earnedBadges(Map<ScoredSustainabilityDimension, Long> storedCounts, Set<Badge> held, List<ScoredBasketProduct> products) {
		if (products.isEmpty()) {
			return List.of();
		}
		return Arrays.stream(Badge.values())
				.filter(badge -> !held.contains(badge))
				.filter(badge -> {
					var requirement = requirementOf(badge);
					return storedCounts.get(requirement.dimension()) + countAboveThreshold(products, requirement.dimension()) >= requirement.products();
				})
				.toList();
	}

	private static long countAboveThreshold(List<ScoredBasketProduct> products, ScoredSustainabilityDimension dimension) {
		return products.stream().filter(product -> product.normalisedScores().get(dimension) > NORMALISED_SCORE_THRESHOLD).count();
	}

	private static Requirement requirementOf(Badge badge) {
		return switch (badge) {
			case ENVIRONMENTAL_HERO_1 -> new Requirement(ENVIRONMENT, LEVEL_1_PRODUCTS);
			case ENVIRONMENTAL_HERO_2 -> new Requirement(ENVIRONMENT, LEVEL_2_PRODUCTS);
			case ENVIRONMENTAL_HERO_3 -> new Requirement(ENVIRONMENT, LEVEL_3_PRODUCTS);
			case ANIMAL_WELFARE_HERO_1 -> new Requirement(ANIMAL_WELFARE, LEVEL_1_PRODUCTS);
			case ANIMAL_WELFARE_HERO_2 -> new Requirement(ANIMAL_WELFARE, LEVEL_2_PRODUCTS);
			case ANIMAL_WELFARE_HERO_3 -> new Requirement(ANIMAL_WELFARE, LEVEL_3_PRODUCTS);
			case SOCIO_ECONOMIC_HERO_1 -> new Requirement(SOCIO_ECONOMIC, LEVEL_1_PRODUCTS);
			case SOCIO_ECONOMIC_HERO_2 -> new Requirement(SOCIO_ECONOMIC, LEVEL_2_PRODUCTS);
			case SOCIO_ECONOMIC_HERO_3 -> new Requirement(SOCIO_ECONOMIC, LEVEL_3_PRODUCTS);
			case HEALTH_HERO_1 -> new Requirement(HEALTH, LEVEL_1_PRODUCTS);
			case HEALTH_HERO_2 -> new Requirement(HEALTH, LEVEL_2_PRODUCTS);
			case HEALTH_HERO_3 -> new Requirement(HEALTH, LEVEL_3_PRODUCTS);
		};
	}
}
