package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.scoring.v1.ProductIndicatorsTestUtils.categoryOf;
import static eu.tealhelix.howibuy.scoring.v1.ProductIndicatorsTestUtils.idOf;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.RecommendationType.GOOD_ENOUGH;
import static eu.tealhelix.howibuy.v1.types.RecommendationType.NO_ALTERNATIVE;
import static eu.tealhelix.howibuy.v1.types.RecommendationType.NO_SCORE;
import static eu.tealhelix.howibuy.v1.types.RecommendationType.RECOMMENDED;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIO_ECONOMIC;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import org.junit.jupiter.api.Test;

/**
 * The dimension scores below are chosen so that every expected winner can be computed by hand: the scientific profile
 * is a flat quarter per dimension, so its overall score is the mean of the four, and the personal profile puts all its
 * weight on the environment, so its overall score is the environmental score alone.
 */
class SubstitutionSearchTest {
	private static final double TOLERANCE = 1e-12;

	private static final ArchetypeCategoryId JUICES = categoryOf("juices");
	private static final ArchetypeCategoryId NECTARS = categoryOf("nectars");
	private static final ArchetypeCategoryId SOFT_DRINKS = categoryOf("soft drinks");
	private static final ArchetypeCategoryId WATER = categoryOf("water");
	private static final ArchetypeCategoryId CANDY = categoryOf("candy");

	private static final WeightProfile PERSONAL = WeightProfileTestUtils.profile(Map.of(ENVIRONMENT, 1.0));

	/** Nectars may substitute for juices readily, soft drinks less so, water only barely, and candy not at all. */
	private static final List<SubstitutablePair> MATRIX = List.of(
			new SubstitutablePair(JUICES, JUICES, (short) 5),
			new SubstitutablePair(NECTARS, JUICES, (short) 4),
			new SubstitutablePair(SOFT_DRINKS, JUICES, (short) 3),
			new SubstitutablePair(WATER, JUICES, (short) 2));

	/** A random source for the answers that seek no Pool, and so draw nothing. */
	private static final RandomGenerator NEVER_CONSULTED = () -> {
		throw new AssertionError("The random source was consulted, but there was nothing to draw between");
	};

	/** A random source for Pools of one product at most, from which every number draws the same. */
	private static final RandomGenerator ANY_NUMBER = drawingAt(0.5);

	private static final ScoredProduct REFERENCE = scored("100", JUICES, 0.40, 0.40, 0.40, 0.40);

	/**
	 * Three candidates whose personal Overall Scores lie {@code ln 2 / 15} apart, so that the draw weighs them 1 : 2 : 4
	 * and picks them with probabilities of one, two and four sevenths.
	 */
	private static final ScoredProduct ONCE = scored("410", NECTARS, 0.50, 0.60, 0.60, 0.60);
	private static final ScoredProduct TWICE = scored("420", NECTARS, 0.50 + Math.log(2) / 15, 0.60, 0.60, 0.60);
	private static final ScoredProduct FOUR_TIMES = scored("430", NECTARS, 0.50 + 2 * Math.log(2) / 15, 0.60, 0.60, 0.60);

	@Test
	void saysNothingAboutAReferenceProductWithoutANutriScore() {
		// A product without a Nutri-Score is left out of the scored corpus.
		var better = scored("210", NECTARS, 0.60, 0.60, 0.60, 0.60);
		var search = searchOver(List.of(better));

		var recommendation = search.recommend(REFERENCE.productId(), PERSONAL, Set.of(better.productId()), NEVER_CONSULTED);

		assertThat(recommendation.type()).isEqualTo(NO_SCORE);
		assertThat(recommendation.referenceOverallScore()).as("no Nutri-Score, no Overall Score").isEmpty();
		assertThat(recommendation.alternatives()).as("though a stocked product scores").isEmpty();
	}

	@Test
	void findsTheReferenceProductGoodEnoughAtExactlyTheGate() {
		// 0.80 on the scientific profile, but only 0.20 on the personal one.
		var atTheGate = scored("110", JUICES, 0.20, 1.00, 1.00, 1.00);
		var better = scored("220", NECTARS, 1.00, 1.00, 1.00, 1.00);
		assertThat(scientificScoreOf(atTheGate)).as("precondition: exactly at the gate").isEqualTo(0.80);

		var recommendation = recommend(atTheGate, NEVER_CONSULTED, better);

		assertThat(recommendation.type()).isEqualTo(GOOD_ENOUGH);
		assertThat(recommendation.referenceOverallScore()).hasValue(0.80);
		assertThat(recommendation.alternatives()).as("none is sought, though a better product is stocked").isEmpty();
	}

	@Test
	void seeksAnAlternativeBelowTheGate() {
		// 0.79 on the scientific profile, though 1.00 on the personal one.
		var belowTheGate = scored("120", JUICES, 1.00, 0.80, 0.80, 0.56);
		var better = scored("220", NECTARS, 1.00, 1.00, 1.00, 1.00);

		var recommendation = recommend(belowTheGate, ANY_NUMBER, better);

		assertThat(recommendation.type()).as("the gate is on the scientific Overall Score").isEqualTo(RECOMMENDED);
		assertThat(recommendation.alternatives()).containsExactly(atDegree(4, better));
	}

	@Test
	void reportsTheScientificOverallScoreOfTheReferenceProduct() {
		// 0.40 on the scientific profile, 0.20 on the personal one.
		var reference = scored("130", JUICES, 0.20, 0.40, 0.60, 0.40);
		var better = scored("220", NECTARS, 1.00, 1.00, 1.00, 1.00);

		var recommendation = recommend(reference, ANY_NUMBER, better);

		assertThat(recommendation.type()).isEqualTo(RECOMMENDED);
		assertThat(recommendation.referenceOverallScore()).hasValueCloseTo(0.40, within(TOLERANCE));
	}

	@Test
	void recommendsTheOnlyBetterProductWhateverTheNumberDrawn() {
		var better = scored("230", NECTARS, 0.60, 0.60, 0.60, 0.60);

		var recommendation = recommend(REFERENCE, drawingAt(0.0), better);

		assertThat(recommendation.type()).isEqualTo(RECOMMENDED);
		assertThat(recommendation.referenceOverallScore()).hasValueCloseTo(0.40, within(TOLERANCE));
		assertThat(recommendation.alternatives()).containsExactly(atDegree(4, better));
		assertThat(recommend(REFERENCE, drawingAt(0.999), better).alternatives()).containsExactly(atDegree(4, better));
	}

	@Test
	void findsNoAlternativeWhenNothingStockedIsBetter() {
		var worse = scored("240", NECTARS, 0.10, 0.10, 0.10, 0.10);

		var recommendation = recommend(REFERENCE, ANY_NUMBER, worse);

		assertThat(recommendation.type()).isEqualTo(NO_ALTERNATIVE);
		assertThat(recommendation.referenceOverallScore()).as("below the gate, and said so").hasValueCloseTo(0.40, within(TOLERANCE));
		assertThat(recommendation.alternatives()).isEmpty();
	}

	@Test
	void neverOffersAProductOnlyAsGoodAsTheReferenceProduct() {
		var asGood = scored("250", NECTARS, 0.40, 0.40, 0.40, 0.40);

		assertThat(recommend(REFERENCE, ANY_NUMBER, asGood).type()).as("no improvement").isEqualTo(NO_ALTERNATIVE);
	}

	@Test
	void neverOffersTheReferenceProductItself() {
		var recommendation = recommendStocking(Set.of(REFERENCE.productId()), REFERENCE, ANY_NUMBER);

		assertThat(recommendation.type()).as("stocked, and of a category substitutable for itself at degree 5")
				.isEqualTo(NO_ALTERNATIVE);
	}

	@Test
	void neverOffersAProductThatIsPersonallyPreferredButScientificallyWorse() {
		// 1.00 on the personal profile, but 0.25 on the scientific one, below the reference product's 0.40.
		var temptingButWorse = scored("260", NECTARS, 1.00, 0.00, 0.00, 0.00);
		var better = scored("270", NECTARS, 0.50, 0.50, 0.50, 0.50);

		var recommendation = recommend(REFERENCE, ANY_NUMBER, temptingButWorse, better);

		assertThat(recommendation.alternatives()).as("the user's weights choose among the better products, never which are better")
				.containsExactly(atDegree(4, better));
	}

	@Test
	void offersOneAlternativeAtEachOfTheDegreesFiveFourAndThreeTheClosestFirst() {
		var sameCategory = scored("280", JUICES, 0.60, 0.60, 0.60, 0.60);
		var nectar = scored("290", NECTARS, 0.60, 0.60, 0.60, 0.60);
		var softDrink = scored("310", SOFT_DRINKS, 0.60, 0.60, 0.60, 0.60);

		var recommendation = recommend(REFERENCE, ANY_NUMBER, softDrink, nectar, sameCategory);

		assertThat(recommendation.type()).isEqualTo(RECOMMENDED);
		assertThat(recommendation.alternatives()).as("whatever order the corpus comes in")
				.containsExactly(atDegree(5, sameCategory), atDegree(4, nectar), atDegree(3, softDrink));
	}

	@Test
	void skipsADegreeAtWhichNothingBetterIsStocked() {
		var sameCategory = scored("280", JUICES, 0.60, 0.60, 0.60, 0.60);
		var worseNectar = scored("290", NECTARS, 0.10, 0.10, 0.10, 0.10);
		var softDrink = scored("310", SOFT_DRINKS, 0.60, 0.60, 0.60, 0.60);

		var recommendation = recommend(REFERENCE, ANY_NUMBER, sameCategory, worseNectar, softDrink);

		assertThat(recommendation.alternatives()).containsExactly(atDegree(5, sameCategory), atDegree(3, softDrink));
	}

	@Test
	void admitsNoCategoryLessSubstitutableThanDegreeThree() {
		var water = scored("320", WATER, 1.00, 1.00, 1.00, 1.00);
		var candy = scored("330", CANDY, 1.00, 1.00, 1.00, 1.00);

		assertThat(recommend(REFERENCE, ANY_NUMBER, water, candy).type())
				.as("degree 2, and a category missing from the matrix").isEqualTo(NO_ALTERNATIVE);
	}

	@Test
	void neverOffersAProductTheCatalogueDoesNotStock() {
		var stocked = scored("340", NECTARS, 0.60, 0.60, 0.60, 0.60);
		var unstocked = scored("350", NECTARS, 1.00, 1.00, 1.00, 1.00);

		var recommendation = recommendStocking(Set.of(stocked.productId()), REFERENCE, ANY_NUMBER, stocked, unstocked);

		assertThat(recommendation.alternatives()).as("the best product is not in the Catalogue").containsExactly(atDegree(4, stocked));
	}

	/**
	 * The candidates take their shares of the total weight in the order of their AGB codes, so a random number in the
	 * first seventh picks the first, one in the next two sevenths the second, and one in the last four the third.
	 */
	@Test
	void drawsTheCandidateInWhoseShareOfTheWeightTheRandomNumberFalls() {
		assertThat(recommend(REFERENCE, drawingAt(1.0 / 14), ONCE, TWICE, FOUR_TIMES).alternatives()).containsExactly(atDegree(4, ONCE));
		assertThat(recommend(REFERENCE, drawingAt(2.0 / 7), ONCE, TWICE, FOUR_TIMES).alternatives()).containsExactly(atDegree(4, TWICE));
		assertThat(recommend(REFERENCE, drawingAt(5.0 / 7), ONCE, TWICE, FOUR_TIMES).alternatives()).containsExactly(atDegree(4, FOUR_TIMES));
	}

	@Test
	void drawsInProportionToTheExponentialOfFifteenTimesThePersonalOverallScore() {
		int draws = 100_000;
		var random = new Random(42);
		var drawn = new HashMap<ScoredProduct, Integer>();

		for (int i = 0; i < draws; i++) {
			drawn.merge(recommend(REFERENCE, random, ONCE, TWICE, FOUR_TIMES).alternatives().getFirst().product(), 1, Integer::sum);
		}

		assertThat((double) drawn.get(ONCE) / draws).isCloseTo(1.0 / 7, within(0.005));
		assertThat((double) drawn.get(TWICE) / draws).isCloseTo(2.0 / 7, within(0.005));
		assertThat((double) drawn.get(FOUR_TIMES) / draws).isCloseTo(4.0 / 7, within(0.005));
	}

	@Test
	void drawsTheSameCandidateWhateverOrderTheCorpusComesIn() {
		var first = scored("510", NECTARS, 0.60, 0.60, 0.60, 0.60);
		var second = scored("520", NECTARS, 0.60, 0.60, 0.60, 0.60);
		var third = scored("530", NECTARS, 0.60, 0.60, 0.60, 0.60);

		var oneOrder = recommend(REFERENCE, drawingAt(0.1), first, second, third);
		var otherOrder = recommend(REFERENCE, drawingAt(0.1), third, second, first);

		assertThat(oneOrder.alternatives()).as("the first third of the weight belongs to the lowest AGB code").containsExactly(atDegree(4, first));
		assertThat(otherOrder.alternatives()).as("whichever comes first in the corpus").containsExactly(atDegree(4, first));
	}

	/**
	 * Each degree takes its number in turn, 5, 4 and 3. Nothing better is stocked at degree 5, and the number it takes
	 * would draw the last of the nectars.
	 */
	@Test
	void aDegreeAtWhichNothingBetterIsStockedStillTakesItsNumber() {
		var recommendation = recommend(REFERENCE, drawingInTurn(0.99, 1.0 / 14, 0.99), ONCE, TWICE, FOUR_TIMES);

		assertThat(recommendation.alternatives()).as("degree 4 draws with the second number").containsExactly(atDegree(4, ONCE));
	}

	@Test
	void aDegreeWithASingleBetterProductStockedStillTakesItsNumber() {
		var juice = scored("610", JUICES, 0.60, 0.60, 0.60, 0.60);

		var recommendation = recommend(REFERENCE, drawingInTurn(0.99, 1.0 / 14, 0.99), juice, ONCE, TWICE, FOUR_TIMES);

		assertThat(recommendation.alternatives()).as("degree 4 draws with the second number")
				.containsExactly(atDegree(5, juice), atDegree(4, ONCE));
	}

	@Test
	void stockingAnotherProductAtOneDegreeLeavesTheAlternativesAtTheOthersAlone() {
		var juice = scored("610", JUICES, 0.60, 0.60, 0.60, 0.60);
		var anotherJuice = scored("620", JUICES, 0.60, 0.60, 0.60, 0.60);
		var softDrink = scored("630", SOFT_DRINKS, 0.60, 0.60, 0.60, 0.60);
		var anotherSoftDrink = scored("640", SOFT_DRINKS, 0.60, 0.60, 0.60, 0.60);

		for (long seed = 0; seed < 50; seed++) {
			var before = recommend(REFERENCE, new Random(seed), juice, ONCE, TWICE, FOUR_TIMES, softDrink, anotherSoftDrink);
			var after = recommend(REFERENCE, new Random(seed), juice, anotherJuice, ONCE, TWICE, FOUR_TIMES, softDrink, anotherSoftDrink);

			assertThat(after.alternatives().subList(1, 3)).as("seed %d, a second juice stocked at degree 5", seed)
					.isEqualTo(before.alternatives().subList(1, 3));
		}
	}

	/**
	 * Recommends against the reference product in a Catalogue that stocks every other product given.
	 */
	private static Recommendation recommend(ScoredProduct reference, RandomGenerator random, ScoredProduct... others) {
		return recommendStocking(Stream.of(others).map(ScoredProduct::productId).collect(toSet()), reference, random, others);
	}

	private static Recommendation recommendStocking(
			Set<ArchetypeProductId> stocked, ScoredProduct reference, RandomGenerator random, ScoredProduct... others) {
		var products = new ArrayList<ScoredProduct>();
		products.add(reference);
		products.addAll(List.of(others));
		return searchOver(products).recommend(reference.productId(), PERSONAL, stocked, random);
	}

	private static SubstitutionSearch searchOver(List<ScoredProduct> products) {
		return new SubstitutionSearch(ScoredCorpus.of(products), SubstitutabilityMatrix.of(MATRIX));
	}

	private static double scientificScoreOf(ScoredProduct product) {
		return product.overallScore(ScientificWeights.profile());
	}

	private static Alternative atDegree(int substitutability, ScoredProduct product) {
		return new Alternative(product, (short) substitutability);
	}

	/**
	 * A random source whose every draw lands at the given fraction of the candidates' total weight.
	 */
	private static RandomGenerator drawingAt(double fraction) {
		return new RandomGenerator() {
			@Override
			public double nextDouble() {
				return fraction;
			}

			@Override
			public long nextLong() {
				throw new UnsupportedOperationException("The draw takes a double");
			}
		};
	}

	/**
	 * A random source whose draws land at the given fractions of the candidates' total weight in turn, and that has no
	 * more numbers to give.
	 */
	private static RandomGenerator drawingInTurn(double... fractions) {
		return new RandomGenerator() {
			private int taken;

			@Override
			public double nextDouble() {
				if (taken == fractions.length) throw new AssertionError("More numbers were taken than there are degrees to draw at");
				return fractions[taken++];
			}

			@Override
			public long nextLong() {
				throw new UnsupportedOperationException("The draw takes a double");
			}
		};
	}

	private static ScoredProduct scored(String agbCode, ArchetypeCategoryId categoryId, double environment, double animalWelfare, double socioEconomic, double health) {
		var scores = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		scores.put(ENVIRONMENT, environment);
		scores.put(ANIMAL_WELFARE, animalWelfare);
		scores.put(SOCIO_ECONOMIC, socioEconomic);
		scores.put(HEALTH, health);
		return new ScoredProduct(idOf(agbCode), categoryId, agbCode, scores);
	}
}
