package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.GOOD_ENOUGH;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.NO_SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.scoring.v1.SubstitutionSettings;
import eu.tealhelix.howibuy.services.model.ArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableSubstitutability;
import eu.tealhelix.howibuy.services.model.Substitutability;
import eu.tealhelix.howibuy.v1.model.AlternativeForProduct;
import eu.tealhelix.howibuy.v1.types.AlternativeForProductType;
import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.ImmutableWeightProfile;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.SubstitutabilityLevel;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeCategoryIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import org.junit.jupiter.api.Test;

/**
 * The fixture is built so every expected score can be computed by hand.
 * <p>
 * Each product's indicators are set to a per-dimension fraction of one tiny base value — tiny so that no single score
 * comes near an outlier threshold and the plain min–max applies, and a fraction because min–max is scale-free: a
 * product at fraction {@code f} of a dimension whose corpus spans 0 to 1 normalises to exactly {@code 1 - f}. So a
 * product declared at {@code (0.5, 0, 0)} scores 0.5 on environment and 1.0 on animal welfare and social, and its
 * health score comes straight off its Nutri-Score.
 * <p>
 * The scientific profile weights the four dimensions equally, making its overall score their mean; the personal
 * profile below puts everything on the environment, making its overall score the environmental score alone.
 */
class ScoredArchetypesTest {
	private static final double BASE_VALUE = 1e-9;
	private static final double TOLERANCE = 1e-9;

	private static final ArchetypeCategoryId L2_JUICES = categoryId("juices");
	private static final ArchetypeCategoryId L2_WATER = categoryId("water");
	private static final ArchetypeCategoryId L2_CANDY = categoryId("candy");

	/** Water substitutes for juices only barely, and candy not at all — it is absent from the matrix. */
	private static final List<Substitutability> MATRIX = List.of(
			substitutability(L2_JUICES, L2_JUICES, (short) 5),
			substitutability(L2_WATER, L2_JUICES, (short) 1));

	private static final ArchetypeProductId REFERENCE = productId("reference");
	private static final ArchetypeProductId GREENEST = productId("greenest");
	private static final ArchetypeProductId SOUNDEST = productId("soundest");
	private static final ArchetypeProductId WORST = productId("worst");
	private static final ArchetypeProductId SPIRITS = productId("spirits");
	private static final ArchetypeProductId CANDY_BAR = productId("candy bar");
	private static final ArchetypeProductId SPRING_WATER = productId("spring water");

	/*
	 * Normalised scores and the two overall scores that follow from them:
	 *
	 *                    E     AW    S     H     scientific  personal
	 * Reference juice    0.5   0.5   0.5   0.75  0.5625      0.5
	 * Greenest juice     1.0   0.5   0.5   1.0   0.75        1.0
	 * Soundest juice     0.5   1.0   1.0   1.0   0.875       0.5
	 * Worst juice        0.0   0.0   0.0   0.0   0.0         0.0
	 * Spring water       1.0   1.0   1.0   1.0   1.0         1.0
	 * Candy bar          0.5   0.5   0.5   1.0   0.625       0.5
	 * Spirits            —     —     —     —     none        none
	 */
	private static final List<ArchetypeProductImpacts> CORPUS = List.of(
			impacts(REFERENCE, "Reference juice", "100", L2_JUICES, 0.5, 0.5, 0.5, "Nutriscore_B"),
			impacts(GREENEST, "Greenest juice", "200", L2_JUICES, 0.0, 0.5, 0.5, "Nutriscore_A"),
			impacts(SOUNDEST, "Soundest juice", "300", L2_JUICES, 0.5, 0.0, 0.0, "Nutriscore_A"),
			impacts(WORST, "Worst juice", "400", L2_JUICES, 1.0, 1.0, 1.0, "Nutriscore_E"),
			impacts(SPIRITS, "Spirits", "500", L2_JUICES, 0.5, 0.5, 0.5, "0"),
			impacts(CANDY_BAR, "Candy bar", "600", L2_CANDY, 0.5, 0.5, 0.5, "Nutriscore_A"),
			impacts(SPRING_WATER, "Spring water", "700", L2_WATER, 0.0, 0.0, 0.0, "Nutriscore_A"));

	private static final WeightProfile PERSONAL = personalProfileOnEnvironment();

	@Test
	void reportsTheBestAlternativeUnderEachCriterionScoredByThatCriterion() {
		var best = archetypes(SubstitutabilityLevel.SMALL).recommendationsFor(REFERENCE, PERSONAL);

		assertAlternative(best.personal(), SUGGESTION, GREENEST, "Greenest juice", 0.5, 1.0);
		assertAlternative(best.scientific(), SUGGESTION, SOUNDEST, "Soundest juice", 0.5625, 0.875);
		assertAlternative(best.combined(), SUGGESTION, GREENEST, "Greenest juice", 0.525, 0.9);
	}

	@Test
	void reportsTheReferenceProductItselfAsGoodEnoughWhenNothingEligibleBeatsIt() {
		var best = archetypes(SubstitutabilityLevel.SMALL).recommendationsFor(SOUNDEST, PERSONAL);

		assertAlternative(best.personal(), GOOD_ENOUGH, SOUNDEST, "Soundest juice", 0.5, 0.5);
		assertAlternative(best.scientific(), GOOD_ENOUGH, SOUNDEST, "Soundest juice", 0.875, 0.875);
		assertAlternative(best.combined(), GOOD_ENOUGH, SOUNDEST, "Soundest juice", 0.65, 0.65);
	}

	@Test
	void reportsNoSuggestionForAProductOutsideTheNutriScoreScheme() {
		var best = archetypes(SubstitutabilityLevel.SMALL).recommendationsFor(SPIRITS, PERSONAL);

		assertNoSuggestion(best.personal());
		assertNoSuggestion(best.scientific());
		assertNoSuggestion(best.combined());
	}

	@Test
	void reportsNoSuggestionWhenNothingSubstitutesForTheReferenceCategory() {
		var best = archetypes(SubstitutabilityLevel.SMALL).recommendationsFor(CANDY_BAR, PERSONAL);

		assertNoSuggestion(best.personal());
		assertNoSuggestion(best.scientific());
		assertNoSuggestion(best.combined());
	}

	@Test
	void neverRecommendsAProductThatCannotBeScoredItself() {
		var best = archetypes(SubstitutabilityLevel.LARGE).recommendationsFor(REFERENCE, PERSONAL);

		assertTrue(
				List.of(best.personal(), best.scientific(), best.combined()).stream()
						.noneMatch(alternative -> SPIRITS.equals(alternative.getArchetypeProductId())),
				"Spirits shares the reference's category and would be eligible, but has no overall score at all");
	}

	@Test
	void reachesAMoreDistantCategoryOnlyAtALargerSubstitutabilityLevel() {
		var atSmall = archetypes(SubstitutabilityLevel.SMALL).recommendationsFor(REFERENCE, PERSONAL);
		var atLarge = archetypes(SubstitutabilityLevel.LARGE).recommendationsFor(REFERENCE, PERSONAL);

		assertEquals(SOUNDEST, atSmall.scientific().getArchetypeProductId(),
				"water substitutes for juices at degree 1, below the small level's cut-off");
		assertAlternative(atLarge.scientific(), SUGGESTION, SPRING_WATER, "Spring water", 0.5625, 1.0);
	}

	private static ScoredArchetypes archetypes(SubstitutabilityLevel level) {
		return ScoredArchetypes.of(CORPUS, MATRIX, SubstitutionSettings.defaults().at(level));
	}

	private static void assertAlternative(
			AlternativeForProduct alternative, AlternativeForProductType type,
			ArchetypeProductId productId, String name, double referenceScore, double alternativeScore) {
		assertEquals(type, alternative.getType());
		assertEquals(productId, alternative.getArchetypeProductId(), "the recommended archetype, by id");
		assertEquals(name, alternative.getName());
		assertEquals(referenceScore, alternative.getReferenceOverallScore(), TOLERANCE, "the reference product's own score under this criterion");
		assertEquals(alternativeScore, alternative.getAlternativeOverallScore(), TOLERANCE, "the recommended product's score under this criterion");
	}

	private static void assertNoSuggestion(AlternativeForProduct alternative) {
		assertEquals(NO_SUGGESTION, alternative.getType());
		assertNull(alternative.getArchetypeProductId(), "no product is named when none is recommended");
		assertNull(alternative.getName());
		assertNull(alternative.getReferenceOverallScore());
		assertNull(alternative.getAlternativeOverallScore());
	}

	/**
	 * A user who cares about nothing but the environment, so that the personal overall score is one of the four
	 * normalised scores rather than a blend of them.
	 */
	private static WeightProfile personalProfileOnEnvironment() {
		var dimensionWeights = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		for (var dimension : ScoredSustainabilityDimension.values()) {
			dimensionWeights.put(dimension, dimension == ENVIRONMENT ? 1.0 : 0.0);
		}
		return ImmutableWeightProfile.builder()
				.indicatorWeights(ScientificWeights.profile().getIndicatorWeights())
				.dimensionWeights(dimensionWeights)
				.build();
	}

	/**
	 * Ids derived from a name, so that a failure names a product a reader can find in the test rather than an opaque
	 * UUID.
	 */
	private static ArchetypeProductId productId(String name) {
		return new ArchetypeProductIdImpl(UUID.nameUUIDFromBytes(name.getBytes()).toString());
	}

	private static ArchetypeCategoryId categoryId(String name) {
		return new ArchetypeCategoryIdImpl(UUID.nameUUIDFromBytes(name.getBytes()).toString());
	}

	private static ArchetypeProductImpacts impacts(
			ArchetypeProductId id, String name, String agbCode, ArchetypeCategoryId l2CategoryId,
			double environment, double animalWelfare, double social, String nutriScore) {
		var values = new EnumMap<SustainabilityIndicator, Double>(SustainabilityIndicator.class);
		for (var indicator : SustainabilityIndicator.values()) {
			values.put(indicator, BASE_VALUE * switch (indicator.getDimension()) {
				case ENVIRONMENT -> environment;
				case ANIMAL_WELFARE -> animalWelfare;
				case SOCIAL -> social;
				case HEALTH -> 0.0;
			});
		}
		return ImmutableArchetypeProductImpacts.builder()
				.id(id)
				.name(name)
				.agbCode(agbCode)
				.l2CategoryId(l2CategoryId)
				.indicatorValues(Map.copyOf(values))
				.nutriScore(nutriScore)
				.build();
	}

	private static Substitutability substitutability(ArchetypeCategoryId fromCategoryId, ArchetypeCategoryId toCategoryId, short degree) {
		return ImmutableSubstitutability.builder()
				.fromCategoryId(fromCategoryId)
				.toCategoryId(toCategoryId)
				.degree(degree)
				.build();
	}
}
