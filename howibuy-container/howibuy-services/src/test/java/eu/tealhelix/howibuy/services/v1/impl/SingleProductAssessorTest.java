package eu.tealhelix.howibuy.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.scoring.v1.SubstitutionSettings;
import eu.tealhelix.howibuy.services.model.ArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableSubstitutability;
import eu.tealhelix.howibuy.services.model.Substitutability;
import eu.tealhelix.howibuy.services.v1.types.ArchetypeMatchOutcome;
import eu.tealhelix.howibuy.v1.model.ImmutableMatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.ImmutableProductData;
import eu.tealhelix.howibuy.v1.model.MatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductData;
import eu.tealhelix.howibuy.v1.types.AlternativeForProductType;
import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeCategoryIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import io.smallrye.mutiny.Uni;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.jboss.weld.junit5.auto.ExcludeBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@EnableAutoWeld
@AddBeanClasses(SingleProductAssessor.class)
@ExtendWith(MockitoExtension.class)
public class SingleProductAssessorTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final ProductData PRODUCT = ImmutableProductData.builder()
			.productKey(new ProductKeyImpl("product-key"))
			.language(Locale.ENGLISH)
			.name("Freshly squeezed orange juice")
			.price(new BigDecimal("2.50"))
			.currency(Currency.getInstance("EUR"))
			.build();

	private static final ArchetypeCategoryId L2_JUICES = new ArchetypeCategoryIdImpl("00000000-0000-0000-0000-000000000002");

	/** The archetype the product matches, and a better one beside it. */
	private static final ArchetypeProductId PICKED_ARCHETYPE = new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000fc");
	private static final ArchetypeProductId BETTER_ARCHETYPE = new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000fb");

	private static final MatchDiagnostics MATCHED_PATH = diagnostics("Beverages", "Juices", "Orange juice", "Tropicana");
	private static final MatchDiagnostics PARTIAL_PATH = diagnostics("Beverages", "Juices", null, null);

	@Produces
	@Mock
	@ExcludeBean
	ArchetypeMatching archetypeMatching;

	@Produces
	@Mock
	@ExcludeBean
	ArchetypeCorpus archetypeCorpus;

	@Inject
	SingleProductAssessor sut;

	@Test
	void reportsTheMatchedPathForTheProductKeyWhenAnArchetypeMatches() {
		mockMatching(new ArchetypeMatchOutcome.Matched(PICKED_ARCHETYPE, MATCHED_PATH));
		mockArchetypeCorpus("Nutriscore_C");

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcome.getType());
		assertEquals(PRODUCT.getProductKey(), outcome.getProductKey());
		assertEquals(MATCHED_PATH, outcome.getDiagnostics());
	}

	@Test
	void reportsFailureToIdentifyWithThePathTakenWhenNoArchetypeMatches() {
		mockMatching(new ArchetypeMatchOutcome.NoMatch(PARTIAL_PATH));

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY, outcome.getType());
		assertEquals(PRODUCT.getProductKey(), outcome.getProductKey());
		assertEquals(PARTIAL_PATH, outcome.getDiagnostics());
		assertNull(outcome.getBestScientificAlternative());
	}

	@Test
	void reportsFailureOtherWithThePathTakenWhenTheReplyOfTheAiIsMalformed() {
		mockMatching(new ArchetypeMatchOutcome.Malformed(PARTIAL_PATH, "category", "Confectionery"));

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.FAILURE_OTHER, outcome.getType());
		assertEquals(PRODUCT.getProductKey(), outcome.getProductKey());
		assertEquals(PARTIAL_PATH, outcome.getDiagnostics());
		assertNull(outcome.getBestScientificAlternative());
	}

	@Test
	void reportsTheBestAlternativeFromTheScoredCorpusForTheMatchedArchetype() {
		mockMatching(new ArchetypeMatchOutcome.Matched(PICKED_ARCHETYPE, MATCHED_PATH));
		mockArchetypeCorpus("Nutriscore_C");

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcome.getType());
		var alternative = outcome.getBestScientificAlternative();
		assertEquals(AlternativeForProductType.SUGGESTION, alternative.getType());
		assertEquals(BETTER_ARCHETYPE, alternative.getArchetypeProductId());
		assertEquals("Store brand", alternative.getName(), "graded A where the matched archetype is only graded C");
		assertEquals("Beverages", alternative.getL1Category(), "the recommended archetype's own taxonomy path");
		assertEquals("Juices", alternative.getL2Category());
		assertEquals("Orange juice", alternative.getL3Category());
		assertEquals(0.875, alternative.getReferenceOverallScore(), 1e-9);
		assertEquals(1.0, alternative.getAlternativeOverallScore(), 1e-9);
	}

	@Test
	void reportsNoSuggestionWhenTheMatchedArchetypeIsOutsideTheNutriScoreScheme() {
		mockMatching(new ArchetypeMatchOutcome.Matched(PICKED_ARCHETYPE, MATCHED_PATH));
		mockArchetypeCorpus("0");

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcome.getType(),
				"the matching succeeded; it is the recommendation that has nothing to say");
		assertEquals(AlternativeForProductType.NO_SUGGESTION, outcome.getBestPersonalAlternative().getType());
		assertEquals(AlternativeForProductType.NO_SUGGESTION, outcome.getBestScientificAlternative().getType());
		assertEquals(AlternativeForProductType.NO_SUGGESTION, outcome.getBestCombinedAlternative().getType());
		assertEquals(MATCHED_PATH, outcome.getDiagnostics(), "and the matched path is still reported");
	}

	private ProductAssessmentOutcome assess() {
		return sut.assessOne(PRODUCT, ScientificWeights.profile()).await().atMost(WAIT);
	}

	private void mockMatching(ArchetypeMatchOutcome outcome) {
		when(archetypeMatching.match(PRODUCT)).thenReturn(Uni.createFrom().item(outcome));
	}

	/**
	 * A corpus of the two candidate archetypes alone. Their indicator values are all zero, so every dimension has a
	 * zero range and normalises flat, leaving the Nutri-Score the only thing that tells the two apart: the store brand
	 * is graded A and the one the product matches only C.
	 */
	private void mockArchetypeCorpus(String pickedProductNutriScore) {
		var corpus = List.of(
				impacts(PICKED_ARCHETYPE, "Tropicana", "agb-tropicana", pickedProductNutriScore),
				impacts(BETTER_ARCHETYPE, "Store brand", "agb-store-brand", "Nutriscore_A"));
		List<Substitutability> matrix = List.of(ImmutableSubstitutability.builder()
				.fromCategoryId(L2_JUICES).toCategoryId(L2_JUICES).degree((short) 5).build());
		when(archetypeCorpus.scoredArchetypes()).thenReturn(Uni.createFrom().item(
				ScoredArchetypes.of(corpus, matrix, SubstitutionSettings.defaults())));
	}

	private static ArchetypeProductImpacts impacts(ArchetypeProductId id, String name, String agbCode, String nutriScore) {
		var values = new EnumMap<SustainabilityIndicator, Double>(SustainabilityIndicator.class);
		for (var indicator : SustainabilityIndicator.values()) {
			values.put(indicator, 0.0);
		}
		return ImmutableArchetypeProductImpacts.builder()
				.id(id)
				.name(name)
				.agbCode(agbCode)
				.l2CategoryId(L2_JUICES)
				.l1CategoryName("Beverages")
				.l2CategoryName("Juices")
				.l3CategoryName("Orange juice")
				.indicatorValues(values)
				.nutriScore(nutriScore)
				.build();
	}

	private static MatchDiagnostics diagnostics(String l1Category, String l2Category, String l3Category, String archetypeProduct) {
		return ImmutableMatchDiagnostics.builder()
				.l1Category(l1Category)
				.l2Category(l2Category)
				.l3Category(l3Category)
				.archetypeProduct(archetypeProduct)
				.build();
	}
}
