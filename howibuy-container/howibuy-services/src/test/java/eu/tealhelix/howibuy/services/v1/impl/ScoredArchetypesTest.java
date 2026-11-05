package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.impacts;
import static eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.personalProfileOnEnvironment;
import static eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.productId;
import static eu.tealhelix.howibuy.v1.types.RecommendationType.GOOD_ENOUGH;
import static eu.tealhelix.howibuy.v1.types.RecommendationType.NO_ALTERNATIVE;
import static eu.tealhelix.howibuy.v1.types.RecommendationType.NO_SCORE;
import static eu.tealhelix.howibuy.v1.types.RecommendationType.RECOMMENDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.services.model.ArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableSubstitutability;
import eu.tealhelix.howibuy.services.model.Substitutability;
import eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.Branch;
import eu.tealhelix.howibuy.v1.model.AlternativeForProduct;
import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
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

	/** Water substitutes for juices less readily than another juice, and candy not at all — it is absent from the matrix. */
	private static final List<Substitutability> MATRIX = List.of(
			substitutability(JUICES.id(), JUICES.id(), (short) 5),
			substitutability(WATER.id(), JUICES.id(), (short) 3));

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

	/** A random source for the answers that seek no Pool, and so draw nothing. */
	private static final RandomGenerator NEVER_CONSULTED = () -> {
		throw new AssertionError("The random source was consulted, but there was nothing to draw between");
	};

	/** A random source for the answers here that seek Pools, none of which holds more than one product to draw. */
	private static final RandomGenerator ANY_DRAW = new Random(0);

	@Test
	void recommendsTheAlternativeDrawnFromThePoolScoredScientifically() {
		var recommendation = archetypes().recommendationFor(REFERENCE, PERSONAL, Set.of(GREENEST), ANY_DRAW);

		assertEquals(RECOMMENDED, recommendation.type());
		assertEquals(0.5625, recommendation.referenceOverallScore(), TOLERANCE, "the reference product's scientific score");
		assertEquals(1, recommendation.alternatives().size());
		var alternative = recommendation.alternatives().getFirst();
		var archetype = alternative.getArchetype();
		assertEquals(GREENEST, archetype.getId(), "the Alternative, by id");
		assertEquals("Greenest juice", archetype.getName());
		assertEquals(JUICES.l1(), archetype.getL1Category(), "the SAFAD path the Alternative sits in");
		assertEquals(JUICES.l2(), archetype.getL2Category());
		assertEquals(JUICES.l3(), archetype.getL3Category());
		assertEquals(0.75, alternative.getOverallScore(), TOLERANCE,
				"its scientific score, the one sure to beat the reference's, where its personal score is 1.0");
		assertEquals(5, alternative.getSubstitutability(), "of the reference's own category");
		assertNull(alternative.getProduct(), "the product of the Catalogue is chosen after the Recommendation");
	}

	@Test
	void offersAnAlternativeAtEachDegreeOfSubstitutabilityTheClosestFirst() {
		var recommendation = archetypes().recommendationFor(REFERENCE, PERSONAL, Set.of(SPRING_WATER, GREENEST), ANY_DRAW);

		assertEquals(RECOMMENDED, recommendation.type());
		assertEquals(List.of(GREENEST, SPRING_WATER), recommendation.alternatives().stream().map(alternative -> alternative.getArchetype().getId()).toList());
		assertEquals(List.of(5, 3), recommendation.alternatives().stream().map(AlternativeForProduct::getSubstitutability).toList());
		assertEquals(1.0, recommendation.alternatives().getLast().getOverallScore(), TOLERANCE, "each told on its own scientific score");
	}

	@Test
	void findsAReferenceProductAtOrAboveTheGateGoodEnough() {
		var recommendation = archetypes().recommendationFor(SOUNDEST, PERSONAL, ALL_STOCKED, NEVER_CONSULTED);

		assertEquals(GOOD_ENOUGH, recommendation.type());
		assertEquals(0.875, recommendation.referenceOverallScore(), TOLERANCE);
		assertTrue(recommendation.alternatives().isEmpty());
	}

	@Test
	void findsNoAlternativeWhenNothingSubstitutesForTheReferenceCategory() {
		var recommendation = archetypes().recommendationFor(CANDY_BAR, PERSONAL, ALL_STOCKED, ANY_DRAW);

		assertEquals(NO_ALTERNATIVE, recommendation.type());
		assertEquals(0.625, recommendation.referenceOverallScore(), TOLERANCE);
		assertTrue(recommendation.alternatives().isEmpty());
	}

	@Test
	void saysNothingAboutAProductOutsideTheNutriScoreScheme() {
		var recommendation = archetypes().recommendationFor(SPIRITS, PERSONAL, ALL_STOCKED, NEVER_CONSULTED);

		assertEquals(NO_SCORE, recommendation.type());
		assertNull(recommendation.referenceOverallScore(), "Spirits has no health score, so no overall score at all");
		assertTrue(recommendation.alternatives().isEmpty());
	}

	@Test
	void neverOffersAProductOutsideTheNutriScoreScheme() {
		var recommendation = archetypes().recommendationFor(REFERENCE, PERSONAL, Set.of(SPIRITS), ANY_DRAW);

		assertEquals(NO_ALTERNATIVE, recommendation.type(),
				"Spirits shares the reference's category and is stocked, but has no overall score to be better with");
	}

	@Test
	void findsAScoredProductByItsArchetype() {
		var reference = archetypes().find(REFERENCE).orElseThrow();

		assertEquals(REFERENCE, reference.productId());
		assertEquals(0.5625, reference.overallScore(ScientificWeights.profile()), TOLERANCE);
		assertEquals(0.5, reference.overallScore(PERSONAL), TOLERANCE);
	}

	@Test
	void findsNoScoredProductOutsideTheNutriScoreScheme() {
		assertTrue(archetypes().find(SPIRITS).isEmpty(), "Spirits has no health score, so no overall score at all");
	}

	private static ScoredArchetypes archetypes() {
		return ScoredArchetypes.of(CORPUS, MATRIX);
	}

	private static Substitutability substitutability(ArchetypeCategoryId fromCategoryId, ArchetypeCategoryId toCategoryId, short degree) {
		return ImmutableSubstitutability.builder()
				.fromCategoryId(fromCategoryId)
				.toCategoryId(toCategoryId)
				.degree(degree)
				.build();
	}
}
