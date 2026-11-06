package eu.tealhelix.howibuy.services.v1.basket;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIO_ECONOMIC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.howibuy.services.v1.basket.types.ScoredBasketProduct;
import eu.tealhelix.howibuy.v1.types.Badge;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class BadgeRulesTest {
	private static final List<ScoredSustainabilityDimension> DIMENSIONS = List.of(ENVIRONMENT, ANIMAL_WELFARE, SOCIO_ECONOMIC, HEALTH);

	private final BadgeRules sut = new BadgeRules();

	/**
	 * A basket earns a badge when the user's stored products above 0.8 on the Normalised Score of the badge's dimension,
	 * together with the basket's own, reach the badge's number, 50, 100 and 200 for the levels 1, 2 and 3, and the user
	 * does not hold the badge yet.
	 * <p>
	 * The stored counts and the Normalised Scores of each product are written in the order Environment, Animal Welfare,
	 * Socio-economic, Health; the products are separated by commas, and {@code n*} stands for n products of those scores.
	 */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"49 0 0 0      |                                             | 0.9 0 0 0                    | ENVIRONMENTAL_HERO_1",
			"48 0 0 0      |                                             | 0.9 0 0 0                    | ",
			"48 0 0 0      |                                             | 0.9 0 0 0, 0.95 0 0 0        | ENVIRONMENTAL_HERO_1",
			"49 0 0 0      |                                             | 0.8 0 0 0                    | ",
			"49 0 0 0      |                                             | 0.8001 0 0 0                 | ENVIRONMENTAL_HERO_1",
			"49 0 0 0      |                                             | 0 0.9 0.9 0.9                | ",
			"0 99 0 0      | ANIMAL_WELFARE_HERO_1                       | 0 0.9 0 0                    | ANIMAL_WELFARE_HERO_2",
			"0 0 199 0     | SOCIO_ECONOMIC_HERO_1 SOCIO_ECONOMIC_HERO_2 | 0 0 0.9 0                    | SOCIO_ECONOMIC_HERO_3",
			"0 0 0 49      |                                             | 0 0 0 0.9                    | HEALTH_HERO_1",
			"49 0 0 0      |                                             | 51*0.9 0 0 0                 | ENVIRONMENTAL_HERO_1 ENVIRONMENTAL_HERO_2",
			"60 0 0 0      | ENVIRONMENTAL_HERO_1                        | 0.9 0 0 0                    | ",
			"10 0 0 0      | ENVIRONMENTAL_HERO_1                        | 0.9 0 0 0                    | ",
			"49 49 49 49   |                                             | 0.9 0.9 0.9 0.9              | ENVIRONMENTAL_HERO_1 ANIMAL_WELFARE_HERO_1 SOCIO_ECONOMIC_HERO_1 HEALTH_HERO_1",
			"0 49 0 49     |                                             | 0 0 0 0.9, 0 0.9 0 0         | ANIMAL_WELFARE_HERO_1 HEALTH_HERO_1",
			"49 49 49 49   |                                             | 0.8 0.5 0.1 0                | ",
			"120 0 0 0     |                                             | 0.1 0 0 0                    | ENVIRONMENTAL_HERO_1 ENVIRONMENTAL_HERO_2",
	})
	void earnsTheBadgesWhoseNumberTheBasketReaches(String storedCounts, String held, String basket, String earned) {
		assertEquals(badges(earned), sut.earnedBadges(counts(storedCounts), Set.copyOf(badges(held)), products(basket)));
	}

	/**
	 * A basket with nothing to assess earns no badge, even one whose number the stored products alone reach.
	 */
	@ParameterizedTest
	@CsvSource({
			"49 0 0 0",
			"120 0 0 0",
	})
	void earnsNoBadgeWithoutAScoredProduct(String storedCounts) {
		assertEquals(List.of(), sut.earnedBadges(counts(storedCounts), Set.of(), List.of()));
	}

	private static Map<ScoredSustainabilityDimension, Long> counts(String counts) {
		var values = counts.trim().split(" +");
		var result = new EnumMap<ScoredSustainabilityDimension, Long>(ScoredSustainabilityDimension.class);
		for (int i = 0; i < DIMENSIONS.size(); i++) {
			result.put(DIMENSIONS.get(i), Long.parseLong(values[i]));
		}
		return result;
	}

	private static List<Badge> badges(String badges) {
		return badges == null ? List.of() : Arrays.stream(badges.trim().split(" +")).map(Badge::valueOf).toList();
	}

	/**
	 * The products of a basket, keyed by their position; their Overall Scores do not count towards a badge.
	 */
	private static List<ScoredBasketProduct> products(String basket) {
		var products = new ArrayList<ScoredBasketProduct>();
		for (var product : basket.split(",")) {
			var timesAndScores = product.trim().split("\\*");
			var times = timesAndScores.length == 2 ? Integer.parseInt(timesAndScores[0]) : 1;
			var scores = timesAndScores[timesAndScores.length - 1].trim().split(" +");
			var normalisedScores = DIMENSIONS.stream().collect(Collectors.toMap(
					dimension -> dimension, dimension -> Double.parseDouble(scores[DIMENSIONS.indexOf(dimension)])));
			for (var copy : Collections.nCopies(times, normalisedScores)) {
				products.add(new ScoredBasketProduct(new ProductKeyImpl("product-" + products.size()), 0.5, 0.5, copy));
			}
		}
		return products;
	}
}
