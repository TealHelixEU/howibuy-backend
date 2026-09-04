package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.scoring.v1.ProductIndicatorsTestUtils.categoryOf;
import static eu.tealhelix.howibuy.scoring.v1.ProductIndicatorsTestUtils.idOf;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIAL;
import static eu.tealhelix.howibuy.v1.types.SubstitutabilityLevel.LARGE;
import static eu.tealhelix.howibuy.v1.types.SubstitutabilityLevel.MEDIUM;
import static eu.tealhelix.howibuy.v1.types.SubstitutabilityLevel.SMALL;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
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
	private static final ArchetypeCategoryId JUICES = categoryOf("juices");
	private static final ArchetypeCategoryId SOFT_DRINKS = categoryOf("soft drinks");
	private static final ArchetypeCategoryId WATER = categoryOf("water");
	private static final ArchetypeCategoryId CANDY = categoryOf("candy");

	private static final WeightProfile SCIENTIFIC = WeightProfileTestUtils.indicatorsOnly(Map.of());
	private static final WeightProfile PERSONAL = WeightProfileTestUtils.profile(Map.of(), Map.of(ENVIRONMENT, 1.0));

	/** Soft drinks may substitute for juices readily, water only barely, and candy not at all. */
	private static final List<SubstitutablePair> MATRIX = List.of(
			new SubstitutablePair(JUICES, JUICES, (short) 5),
			new SubstitutablePair(SOFT_DRINKS, JUICES, (short) 3),
			new SubstitutablePair(WATER, JUICES, (short) 1));

	private static final ScoredProduct REFERENCE = scored("100", JUICES, 0.40, 0.40, 0.40, 0.40);
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
	void findsNoAlternativeWhenNoCategorySubstitutesForTheReferenceCategory() {
		var search = new SubstitutionSearch(
				ScoredCorpus.of(List.of(REFERENCE, COMPROMISE)),
				SubstitutabilityMatrix.of(List.of(new SubstitutablePair(WATER, CANDY, (short) 5))),
				SCIENTIFIC,
				SubstitutionSettings.defaults());

		var alternatives = search.find(REFERENCE.productId(), PERSONAL);

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

	private static Alternatives search(SubstitutabilityLevel level, ScoredProduct reference, ScoredProduct... others) {
		var products = new ArrayList<ScoredProduct>();
		products.add(reference);
		products.addAll(List.of(others));
		var search = new SubstitutionSearch(
				ScoredCorpus.of(products),
				SubstitutabilityMatrix.of(MATRIX),
				SCIENTIFIC,
				SubstitutionSettings.defaults().at(level));
		return search.find(reference.productId(), PERSONAL);
	}

	private static ScoredProduct scored(String agbCode, ArchetypeCategoryId categoryId, double environment, double animalWelfare, double social, double health) {
		var scores = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		scores.put(ENVIRONMENT, environment);
		scores.put(ANIMAL_WELFARE, animalWelfare);
		scores.put(SOCIAL, social);
		scores.put(HEALTH, health);
		return new ScoredProduct(idOf(agbCode), categoryId, agbCode, scores);
	}
}
