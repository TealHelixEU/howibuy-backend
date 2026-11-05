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
import static eu.tealhelix.howibuy.v1.types.SubstitutabilityLevel.LARGE;
import static eu.tealhelix.howibuy.v1.types.SubstitutabilityLevel.MEDIUM;
import static eu.tealhelix.howibuy.v1.types.SubstitutabilityLevel.SMALL;
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
import eu.tealhelix.howibuy.v1.types.SubstitutabilityLevel;
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

	private static final WeightProfile SCIENTIFIC = WeightProfileTestUtils.profile(Map.of(ENVIRONMENT, 0.25, ANIMAL_WELFARE, 0.25, SOCIO_ECONOMIC, 0.25, HEALTH, 0.25));
	private static final WeightProfile PERSONAL = WeightProfileTestUtils.profile(Map.of(ENVIRONMENT, 1.0));

	/** Nectars may substitute for juices readily, soft drinks less so, water only barely, and candy not at all. */
	private static final List<SubstitutablePair> MATRIX = List.of(
			new SubstitutablePair(JUICES, JUICES, (short) 5),
			new SubstitutablePair(NECTARS, JUICES, (short) 4),
			new SubstitutablePair(SOFT_DRINKS, JUICES, (short) 3),
			new SubstitutablePair(WATER, JUICES, (short) 1));

	/** A random source for the answers that need no draw: a Pool of one is offered as it is, and no other answer draws. */
	private static final RandomGenerator NEVER_CONSULTED = () -> {
		throw new AssertionError("The random source was consulted, but there was nothing to draw between");
	};

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

		var recommendation = recommend(belowTheGate, NEVER_CONSULTED, better);

		assertThat(recommendation.type()).as("the gate is on the scientific Overall Score").isEqualTo(RECOMMENDED);
		assertThat(recommendation.alternatives()).containsExactly(better);
	}

	@Test
	void reportsTheScientificOverallScoreOfTheReferenceProduct() {
		// 0.40 on the scientific profile, 0.20 on the personal one.
		var reference = scored("130", JUICES, 0.20, 0.40, 0.60, 0.40);
		var better = scored("220", NECTARS, 1.00, 1.00, 1.00, 1.00);

		var recommendation = recommend(reference, NEVER_CONSULTED, better);

		assertThat(recommendation.type()).isEqualTo(RECOMMENDED);
		assertThat(recommendation.referenceOverallScore()).hasValueCloseTo(0.40, within(TOLERANCE));
	}

	@Test
	void recommendsTheOnlyBetterProductWithoutDrawing() {
		var better = scored("230", NECTARS, 0.60, 0.60, 0.60, 0.60);

		var recommendation = recommend(REFERENCE, NEVER_CONSULTED, better);

		assertThat(recommendation.type()).isEqualTo(RECOMMENDED);
		assertThat(recommendation.referenceOverallScore()).hasValueCloseTo(0.40, within(TOLERANCE));
		assertThat(recommendation.alternatives()).containsExactly(better);
	}

	@Test
	void findsNoAlternativeWhenNothingStockedIsBetter() {
		var worse = scored("240", NECTARS, 0.10, 0.10, 0.10, 0.10);

		var recommendation = recommend(REFERENCE, NEVER_CONSULTED, worse);

		assertThat(recommendation.type()).isEqualTo(NO_ALTERNATIVE);
		assertThat(recommendation.referenceOverallScore()).as("below the gate, and said so").hasValueCloseTo(0.40, within(TOLERANCE));
		assertThat(recommendation.alternatives()).isEmpty();
	}

	@Test
	void neverOffersAProductOnlyAsGoodAsTheReferenceProduct() {
		var asGood = scored("250", NECTARS, 0.40, 0.40, 0.40, 0.40);

		assertThat(recommend(REFERENCE, NEVER_CONSULTED, asGood).type()).as("no improvement").isEqualTo(NO_ALTERNATIVE);
	}

	@Test
	void neverOffersTheReferenceProductItself() {
		var recommendation = recommendStocking(Set.of(REFERENCE.productId()), REFERENCE, NEVER_CONSULTED);

		assertThat(recommendation.type()).as("stocked, and of a category substitutable for itself at degree 5")
				.isEqualTo(NO_ALTERNATIVE);
	}

	@Test
	void neverOffersAProductThatIsPersonallyPreferredButScientificallyWorse() {
		// 1.00 on the personal profile, but 0.25 on the scientific one, below the reference product's 0.40.
		var temptingButWorse = scored("260", NECTARS, 1.00, 0.00, 0.00, 0.00);
		var better = scored("270", NECTARS, 0.50, 0.50, 0.50, 0.50);

		var recommendation = recommend(REFERENCE, NEVER_CONSULTED, temptingButWorse, better);

		assertThat(recommendation.alternatives()).as("the user's weights choose among the better products, never which are better")
				.containsExactly(better);
	}

	@Test
	void admitsTheCategoriesSubstitutableAtDegreeFourOrFive() {
		var sameCategory = scored("280", JUICES, 0.60, 0.60, 0.60, 0.60);
		var nectar = scored("290", NECTARS, 0.60, 0.60, 0.60, 0.60);

		assertThat(recommend(REFERENCE, NEVER_CONSULTED, sameCategory).alternatives()).as("degree 5").containsExactly(sameCategory);
		assertThat(recommend(REFERENCE, NEVER_CONSULTED, nectar).alternatives()).as("degree 4").containsExactly(nectar);
	}

	@Test
	void admitsNoCategoryLessSubstitutableThanDegreeFour() {
		var softDrink = scored("310", SOFT_DRINKS, 1.00, 1.00, 1.00, 1.00);
		var water = scored("320", WATER, 1.00, 1.00, 1.00, 1.00);
		var candy = scored("330", CANDY, 1.00, 1.00, 1.00, 1.00);

		assertThat(recommend(REFERENCE, NEVER_CONSULTED, softDrink, water, candy).type())
				.as("degree 3, degree 1, and a category missing from the matrix").isEqualTo(NO_ALTERNATIVE);
	}

	@Test
	void neverOffersAProductTheCatalogueDoesNotStock() {
		var stocked = scored("340", NECTARS, 0.60, 0.60, 0.60, 0.60);
		var unstocked = scored("350", NECTARS, 1.00, 1.00, 1.00, 1.00);

		var recommendation = recommendStocking(Set.of(stocked.productId()), REFERENCE, NEVER_CONSULTED, stocked, unstocked);

		assertThat(recommendation.alternatives()).as("the best product is not in the Catalogue").containsExactly(stocked);
	}

	/**
	 * The candidates take their shares of the total weight in the order of their AGB codes, so a random number in the
	 * first seventh picks the first, one in the next two sevenths the second, and one in the last four the third.
	 */
	@Test
	void drawsTheCandidateInWhoseShareOfTheWeightTheRandomNumberFalls() {
		assertThat(recommend(REFERENCE, drawingAt(1.0 / 14), ONCE, TWICE, FOUR_TIMES).alternatives()).containsExactly(ONCE);
		assertThat(recommend(REFERENCE, drawingAt(2.0 / 7), ONCE, TWICE, FOUR_TIMES).alternatives()).containsExactly(TWICE);
		assertThat(recommend(REFERENCE, drawingAt(5.0 / 7), ONCE, TWICE, FOUR_TIMES).alternatives()).containsExactly(FOUR_TIMES);
	}

	@Test
	void drawsInProportionToTheExponentialOfFifteenTimesThePersonalOverallScore() {
		int draws = 100_000;
		var random = new Random(42);
		var drawn = new HashMap<ScoredProduct, Integer>();

		for (int i = 0; i < draws; i++) {
			drawn.merge(recommend(REFERENCE, random, ONCE, TWICE, FOUR_TIMES).alternatives().getFirst(), 1, Integer::sum);
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

		assertThat(oneOrder.alternatives()).as("the first third of the weight belongs to the lowest AGB code").containsExactly(first);
		assertThat(otherOrder.alternatives()).as("whichever comes first in the corpus").containsExactly(first);
	}
	/** Best on the personal score alone: 0.90 environmental, against a scientific mean of 0.45. */
	private static final ScoredProduct PERSONAL_FAVOURITE = scored("200", SOFT_DRINKS, 0.90, 0.30, 0.30, 0.30);
	/** Best on the scientific score alone: a mean of 0.80, but only 0.20 environmental. */
	private static final ScoredProduct SCIENTIFIC_FAVOURITE = scored("300", SOFT_DRINKS, 0.20, 1.00, 1.00, 1.00);
	/** Second on both, and so first on the combined score: 0.6 × 0.80 + 0.4 × 0.65 = 0.74. */
	private static final ScoredProduct COMPROMISE = scored("400", SOFT_DRINKS, 0.80, 0.60, 0.60, 0.60);
	/** Better than everything, but its category is only substitutable at the largest level. */
	private static final ScoredProduct DISTANT = scored("500", WATER, 1.00, 1.00, 1.00, 1.00);

	@Test
	void admitsOnlyTheReferenceCategoryAtTheSmallestLevel() {
		var alternatives = search(SMALL, REFERENCE, PERSONAL_FAVOURITE, SCIENTIFIC_FAVOURITE, COMPROMISE, DISTANT);

		assertThat(alternatives.bestScientific()).as("soft drinks need degree 3, below the small level's cut-off of 4")
				.contains(REFERENCE);
	}

	@Test
	void admitsTheReadilySubstitutableCategoryAtTheMediumLevel() {
		var alternatives = search(MEDIUM, REFERENCE, PERSONAL_FAVOURITE, SCIENTIFIC_FAVOURITE, COMPROMISE, DISTANT);

		assertThat(alternatives.bestScientific()).as("soft drinks qualify at degree 3, water at degree 1 does not")
				.contains(SCIENTIFIC_FAVOURITE);
	}

	@Test
	void admitsTheBarelySubstitutableCategoryAtTheLargestLevel() {
		var alternatives = search(LARGE, REFERENCE, PERSONAL_FAVOURITE, SCIENTIFIC_FAVOURITE, COMPROMISE, DISTANT);

		assertThat(alternatives.bestScientific()).as("water qualifies at degree 1, so the best product overall is reachable")
				.contains(DISTANT);
	}

	@Test
	void neverAdmitsACategoryMissingFromTheMatrix() {
		var unreachable = scored("600", CANDY, 1.00, 1.00, 1.00, 1.00);

		var alternatives = search(LARGE, REFERENCE, COMPROMISE, unreachable);

		assertThat(alternatives.bestScientific()).as("an absent pair means not substitutable at any level")
				.contains(COMPROMISE);
	}

	@Test
	void excludesACandidateThatIsPersonallyPreferredButScientificallyWorse() {
		// 1.00 environmental would win the personal ranking outright, but a mean of 0.25 is below the reference's 0.40.
		var temptingButWorse = scored("700", SOFT_DRINKS, 1.00, 0.00, 0.00, 0.00);

		var alternatives = search(MEDIUM, REFERENCE, temptingButWorse, PERSONAL_FAVOURITE);

		assertThat(alternatives.bestPersonal()).as("personalisation ranks the survivors, it never decides who survives")
				.contains(PERSONAL_FAVOURITE);
	}

	@Test
	void ranksTheSurvivorsThreeWaysThatCanDisagree() {
		var alternatives = search(MEDIUM, REFERENCE, PERSONAL_FAVOURITE, SCIENTIFIC_FAVOURITE, COMPROMISE);

		assertThat(alternatives.bestPersonal()).as("highest environmental score").contains(PERSONAL_FAVOURITE);
		assertThat(alternatives.bestScientific()).as("highest mean across the four dimensions").contains(SCIENTIFIC_FAVOURITE);
		assertThat(alternatives.bestCombined()).as("highest 0.6 personal + 0.4 scientific").contains(COMPROMISE);
	}

	@Test
	void recommendsTheReferenceProductItselfWhenNothingBeatsIt() {
		var worse = scored("800", SOFT_DRINKS, 0.10, 0.10, 0.10, 0.10);

		var alternatives = search(MEDIUM, REFERENCE, worse);

		assertThat(alternatives.reference()).isEqualTo(REFERENCE);
		assertThat(alternatives.bestCombined()).as("the reference is always a candidate for itself, so it wins by default")
				.contains(REFERENCE);
	}

	@Test
	void neverAdmitsAnArchetypeTheCatalogueDoesNotStock() {
		var alternatives = searchStocking(Set.of(PERSONAL_FAVOURITE.productId(), COMPROMISE.productId()),
				MEDIUM, REFERENCE, PERSONAL_FAVOURITE, SCIENTIFIC_FAVOURITE, COMPROMISE);

		assertThat(alternatives.bestScientific()).as("the scientific favourite would win, but nothing in the Catalogue is it")
				.contains(COMPROMISE);
	}

	@Test
	void keepsTheReferenceProductACandidateWhenTheCatalogueDoesNotStockIt() {
		var alternatives = searchStocking(Set.of(), MEDIUM, REFERENCE, PERSONAL_FAVOURITE, SCIENTIFIC_FAVOURITE, COMPROMISE);

		assertThat(alternatives.bestPersonal()).as("the user holds the reference product, wherever it came from")
				.contains(REFERENCE);
		assertThat(alternatives.bestScientific()).contains(REFERENCE);
		assertThat(alternatives.bestCombined()).contains(REFERENCE);
	}

	@Test
	void findsNoAlternativeWhenNoCategorySubstitutesForTheReferenceCategory() {
		var search = new SubstitutionSearch(
				ScoredCorpus.of(List.of(REFERENCE, COMPROMISE)),
				SubstitutabilityMatrix.of(List.of(new SubstitutablePair(WATER, CANDY, (short) 5))),
				SCIENTIFIC,
				SubstitutionSettings.defaults());

		var alternatives = search.find(REFERENCE.productId(), PERSONAL, Set.of(COMPROMISE.productId()));

		assertThat(alternatives.reference()).as("an empty result still says what was searched for").isEqualTo(REFERENCE);
		assertThat(alternatives.bestPersonal()).isEmpty();
		assertThat(alternatives.bestScientific()).isEmpty();
		assertThat(alternatives.bestCombined()).isEmpty();
	}

	@Test
	void breaksTiesOnTheAgbCodeWhateverTheCorpusOrder() {
		var earlierCode = scored("810", SOFT_DRINKS, 0.50, 0.50, 0.50, 0.50);
		var laterCode = scored("820", SOFT_DRINKS, 0.50, 0.50, 0.50, 0.50);

		var oneOrder = search(MEDIUM, REFERENCE, earlierCode, laterCode);
		var otherOrder = search(MEDIUM, REFERENCE, laterCode, earlierCode);

		assertThat(oneOrder.bestCombined()).as("tied candidates resolve to the lower agb code").contains(earlierCode);
		assertThat(otherOrder.bestCombined()).as("and do so however the corpus happens to be ordered").contains(earlierCode);
	}

	@Test
	void defaultsToTheProvisionalDegreeCutOffs() {
		var settings = SubstitutionSettings.defaults();

		assertThat(settings.level()).isEqualTo(SMALL);
		assertThat(settings.minimumDegreeFor(SMALL)).isEqualTo((short) 4);
		assertThat(settings.minimumDegreeFor(MEDIUM)).isEqualTo((short) 3);
		assertThat(settings.minimumDegreeFor(LARGE)).isEqualTo((short) 1);
		assertThat(settings.personalWeight()).isEqualTo(0.6);
		assertThat(settings.scientificWeight()).isEqualTo(0.4);
	}

	/**
	 * Searches a Catalogue that stocks every product but the reference, which is always a candidate anyway.
	 */
	private static Alternatives search(SubstitutabilityLevel level, ScoredProduct reference, ScoredProduct... others) {
		return searchStocking(Stream.of(others).map(ScoredProduct::productId).collect(toSet()), level, reference, others);
	}

	private static Alternatives searchStocking(
			Set<ArchetypeProductId> stocked, SubstitutabilityLevel level, ScoredProduct reference, ScoredProduct... others) {
		var products = new ArrayList<ScoredProduct>();
		products.add(reference);
		products.addAll(List.of(others));
		var search = new SubstitutionSearch(
				ScoredCorpus.of(products),
				SubstitutabilityMatrix.of(MATRIX),
				SCIENTIFIC,
				SubstitutionSettings.defaults().at(level));
		return search.find(reference.productId(), PERSONAL, stocked);
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
		return new SubstitutionSearch(ScoredCorpus.of(products), SubstitutabilityMatrix.of(MATRIX), SCIENTIFIC, SubstitutionSettings.defaults());
	}

	private static double scientificScoreOf(ScoredProduct product) {
		return product.overallScore(ScientificWeights.profile());
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

	private static ScoredProduct scored(String agbCode, ArchetypeCategoryId categoryId, double environment, double animalWelfare, double socioEconomic, double health) {
		var scores = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		scores.put(ENVIRONMENT, environment);
		scores.put(ANIMAL_WELFARE, animalWelfare);
		scores.put(SOCIO_ECONOMIC, socioEconomic);
		scores.put(HEALTH, health);
		return new ScoredProduct(idOf(agbCode), categoryId, agbCode, scores);
	}
}
