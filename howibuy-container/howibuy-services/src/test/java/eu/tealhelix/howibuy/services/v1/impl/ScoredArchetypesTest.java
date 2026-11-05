package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.impacts;
import static eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.personalProfileOnEnvironment;
import static eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.productId;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.GOOD_ENOUGH;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.NO_SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.SUGGESTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.scoring.v1.SubstitutionSettings;
import eu.tealhelix.howibuy.services.model.ArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableSubstitutability;
import eu.tealhelix.howibuy.services.model.Substitutability;
import eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.Branch;
import eu.tealhelix.howibuy.v1.model.AlternativeForProduct;
import eu.tealhelix.howibuy.v1.types.AlternativeForProductType;
import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.SubstitutabilityLevel;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import org.junit.jupiter.api.Test;

/**
 * The fixture is built with {@link ArchetypeImpactsFixtures} so every expected score can be computed by hand.
 * <p>
 * The corpus holds its lowest and its highest impact of every dimension twice, so its scale runs from the one to the
 * other: an environmental impact of 0, 0.5 or 1 normalises to 1, 0.5 or 0, and Nutri-Score points of 0, 5 or 20 to 1,
 * 0.75 or 0. Animal welfare and socio-economic are log-transformed first, around the median of their positive impacts,
 * here 1: an impact of 0, 1 or 3 becomes log 1, log 2 or log 4, and normalises to 1, 0.5 or 0. Spirits, which has no
 * Nutri-Score, has no part in the scale.
 * <p>
 * The scientific profile weights the four dimensions equally, making its overall score their mean; the personal
 * profile below puts everything on the environment, making its overall score the environmental score alone.
 */
class ScoredArchetypesTest {
	private static final double TOLERANCE = 1e-9;

	private static final Branch JUICES = new Branch("Beverages", "Juices", "Fruit juices");
	private static final Branch WATER = new Branch("Beverages", "Water", "Spring waters");
	private static final Branch CANDY = new Branch("Sweets", "Candy", "Candy bars");

	/** Water substitutes for juices only barely, and candy not at all — it is absent from the matrix. */
	private static final List<Substitutability> MATRIX = List.of(
			substitutability(JUICES.id(), JUICES.id(), (short) 5),
			substitutability(WATER.id(), JUICES.id(), (short) 1));

	private static final ArchetypeProductId REFERENCE = productId("reference");
	private static final ArchetypeProductId GREENEST = productId("greenest");
	private static final ArchetypeProductId SOUNDEST = productId("soundest");
	private static final ArchetypeProductId WORST = productId("worst");
	private static final ArchetypeProductId SPIRITS = productId("spirits");
	private static final ArchetypeProductId CANDY_BAR = productId("candy bar");
	private static final ArchetypeProductId WORST_CANDY_BAR = productId("worst candy bar");
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
	 * Worst candy bar    0.0   0.0   0.0   0.0   0.0         0.0
	 * Spirits            —     —     —     —     none        none
	 */
	private static final List<ArchetypeProductImpacts> CORPUS = List.of(
			impacts(REFERENCE, "Reference juice", "100", JUICES, 0.5, 1.0, 1.0, "Nutriscore_B", 5),
			impacts(GREENEST, "Greenest juice", "200", JUICES, 0.0, 1.0, 1.0, "Nutriscore_A", 0),
			impacts(SOUNDEST, "Soundest juice", "300", JUICES, 0.5, 0.0, 0.0, "Nutriscore_A", 0),
			impacts(WORST, "Worst juice", "400", JUICES, 1.0, 3.0, 3.0, "Nutriscore_E", 20),
			impacts(SPIRITS, "Spirits", "500", JUICES, 0.5, 1.0, 1.0, "0", 0),
			impacts(CANDY_BAR, "Candy bar", "600", CANDY, 0.5, 1.0, 1.0, "Nutriscore_A", 0),
			impacts(WORST_CANDY_BAR, "Worst candy bar", "650", CANDY, 1.0, 3.0, 3.0, "Nutriscore_E", 20),
			impacts(SPRING_WATER, "Spring water", "700", WATER, 0.0, 0.0, 0.0, "Nutriscore_A", 0));

	private static final WeightProfile PERSONAL = personalProfileOnEnvironment();
	private static final Set<ArchetypeProductId> ALL_STOCKED = CORPUS.stream().map(ArchetypeProductImpacts::getId).collect(Collectors.toSet());

	@Test
	void reportsTheBestAlternativeUnderEachCriterionScoredByThatCriterion() {
		var best = archetypes(SubstitutabilityLevel.SMALL).recommendationsFor(REFERENCE, PERSONAL, ALL_STOCKED);

		assertAlternative(best.personal(), SUGGESTION, GREENEST, "Greenest juice", JUICES, 0.5, 1.0);
		assertAlternative(best.scientific(), SUGGESTION, SOUNDEST, "Soundest juice", JUICES, 0.5625, 0.875);
		assertAlternative(best.combined(), SUGGESTION, GREENEST, "Greenest juice", JUICES, 0.525, 0.9);
	}

	@Test
	void reportsTheReferenceProductItselfAsGoodEnoughWhenNothingEligibleBeatsIt() {
		var best = archetypes(SubstitutabilityLevel.SMALL).recommendationsFor(SOUNDEST, PERSONAL, ALL_STOCKED);

		assertAlternative(best.personal(), GOOD_ENOUGH, SOUNDEST, "Soundest juice", JUICES, 0.5, 0.5);
		assertAlternative(best.scientific(), GOOD_ENOUGH, SOUNDEST, "Soundest juice", JUICES, 0.875, 0.875);
		assertAlternative(best.combined(), GOOD_ENOUGH, SOUNDEST, "Soundest juice", JUICES, 0.65, 0.65);
	}

	@Test
	void neverSuggestsAnArchetypeTheCatalogueDoesNotStock() {
		var best = archetypes(SubstitutabilityLevel.SMALL).recommendationsFor(REFERENCE, PERSONAL, Set.of(SOUNDEST));

		assertAlternative(best.personal(), GOOD_ENOUGH, REFERENCE, "Reference juice", JUICES, 0.5, 0.5);
		assertAlternative(best.scientific(), SUGGESTION, SOUNDEST, "Soundest juice", JUICES, 0.5625, 0.875);
	}

	@Test
	void reportsNoSuggestionForAProductOutsideTheNutriScoreScheme() {
		var best = archetypes(SubstitutabilityLevel.SMALL).recommendationsFor(SPIRITS, PERSONAL, ALL_STOCKED);

		assertNoSuggestion(best.personal());
		assertNoSuggestion(best.scientific());
		assertNoSuggestion(best.combined());
	}

	@Test
	void reportsNoSuggestionWhenNothingSubstitutesForTheReferenceCategory() {
		var best = archetypes(SubstitutabilityLevel.SMALL).recommendationsFor(CANDY_BAR, PERSONAL, ALL_STOCKED);

		assertNoSuggestion(best.personal());
		assertNoSuggestion(best.scientific());
		assertNoSuggestion(best.combined());
	}

	@Test
	void neverRecommendsAProductThatCannotBeScoredItself() {
		var best = archetypes(SubstitutabilityLevel.LARGE).recommendationsFor(REFERENCE, PERSONAL, ALL_STOCKED);

		assertTrue(
				List.of(best.personal(), best.scientific(), best.combined()).stream()
						.noneMatch(alternative -> alternative.getArchetype() != null && SPIRITS.equals(alternative.getArchetype().getId())),
				"Spirits shares the reference's category and would be eligible, but has no overall score at all");
	}

	@Test
	void reachesAMoreDistantCategoryOnlyAtALargerSubstitutabilityLevel() {
		var atSmall = archetypes(SubstitutabilityLevel.SMALL).recommendationsFor(REFERENCE, PERSONAL, ALL_STOCKED);
		var atLarge = archetypes(SubstitutabilityLevel.LARGE).recommendationsFor(REFERENCE, PERSONAL, ALL_STOCKED);

		assertEquals(SOUNDEST, atSmall.scientific().getArchetype().getId(),
				"water substitutes for juices at degree 1, below the small level's cut-off");
		assertAlternative(atLarge.scientific(), SUGGESTION, SPRING_WATER, "Spring water", WATER, 0.5625, 1.0);
	}

	@Test
	void findsAScoredProductByItsArchetype() {
		var reference = archetypes(SubstitutabilityLevel.SMALL).find(REFERENCE).orElseThrow();

		assertEquals(REFERENCE, reference.productId());
		assertEquals(0.5625, reference.overallScore(ScientificWeights.profile()), TOLERANCE);
		assertEquals(0.5, reference.overallScore(PERSONAL), TOLERANCE);
	}

	@Test
	void findsNoScoredProductOutsideTheNutriScoreScheme() {
		assertTrue(archetypes(SubstitutabilityLevel.SMALL).find(SPIRITS).isEmpty(), "Spirits has no health score, so no overall score at all");
	}

	private static ScoredArchetypes archetypes(SubstitutabilityLevel level) {
		return ScoredArchetypes.of(CORPUS, MATRIX, SubstitutionSettings.defaults().at(level));
	}

	private static void assertAlternative(
			AlternativeForProduct alternative, AlternativeForProductType type,
			ArchetypeProductId productId, String name, Branch branch, double referenceScore, double alternativeScore) {
		assertEquals(type, alternative.getType());
		var archetype = alternative.getArchetype();
		assertEquals(productId, archetype.getId(), "the recommended archetype, by id");
		assertEquals(name, archetype.getName());
		assertEquals(branch.l1(), archetype.getL1Category(), "the SAFAD path the recommended archetype sits in");
		assertEquals(branch.l2(), archetype.getL2Category());
		assertEquals(branch.l3(), archetype.getL3Category());
		assertNull(alternative.getProduct(), "the product of the Catalogue is chosen after the ranking");
		assertEquals(referenceScore, alternative.getReferenceOverallScore(), TOLERANCE, "the reference product's own score under this criterion");
		assertEquals(alternativeScore, alternative.getAlternativeOverallScore(), TOLERANCE, "the recommended product's score under this criterion");
	}

	private static void assertNoSuggestion(AlternativeForProduct alternative) {
		assertEquals(NO_SUGGESTION, alternative.getType());
		assertNull(alternative.getArchetype(), "no archetype is named when none is recommended");
		assertNull(alternative.getReferenceOverallScore());
		assertNull(alternative.getAlternativeOverallScore());
	}

	private static Substitutability substitutability(ArchetypeCategoryId fromCategoryId, ArchetypeCategoryId toCategoryId, short degree) {
		return ImmutableSubstitutability.builder()
				.fromCategoryId(fromCategoryId)
				.toCategoryId(toCategoryId)
				.degree(degree)
				.build();
	}
}
