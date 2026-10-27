package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.IdentificationFailureReason.NOT_CLASSIFIED;
import static eu.tealhelix.howibuy.v1.types.IdentificationFailureReason.UNKNOWN_PRODUCT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.scoring.v1.SubstitutionSettings;
import eu.tealhelix.howibuy.services.model.ArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableSubstitutability;
import eu.tealhelix.howibuy.services.model.Substitutability;
import eu.tealhelix.howibuy.v1.model.IdentificationDiagnostics;
import eu.tealhelix.howibuy.v1.model.ImmutableIdentificationDiagnostics;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.types.AlternativeForProductType;
import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.Classifier;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeCategoryIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.RetailerProductIdImpl;
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

	private static final ProductKey PRODUCT_KEY = new ProductKeyImpl("product-key");

	private static final ArchetypeCategoryId L2_JUICES = new ArchetypeCategoryIdImpl("00000000-0000-0000-0000-000000000002");

	/** The archetype the product is, and a better one beside it. */
	private static final ArchetypeProductId PICKED_ARCHETYPE = new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000fc");
	private static final ArchetypeProductId BETTER_ARCHETYPE = new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000fb");

	private static final IdentificationDiagnostics CLASSIFIED = ImmutableIdentificationDiagnostics.builder()
			.retailerProductId(new RetailerProductIdImpl("6a3e1f0c-2b4d-4e8a-9c71-5d2f8b0a3e14"))
			.classifier(Classifier.EXPERT)
			.needsReview(false)
			.pending(false)
			.archetypeProductId(PICKED_ARCHETYPE)
			.archetypeProductName("Tropicana")
			.build();
	private static final IdentificationDiagnostics NEVER_CLASSIFIED = ImmutableIdentificationDiagnostics.builder()
			.retailerProductId(new RetailerProductIdImpl("6a3e1f0c-2b4d-4e8a-9c71-5d2f8b0a3e14"))
			.build();

	@Produces
	@Mock
	@ExcludeBean
	ArchetypeCorpus archetypeCorpus;

	@Inject
	SingleProductAssessor sut;

	@Test
	void reportsHowTheProductWasIdentifiedForTheProductKeyWhenItsArchetypeIsIdentified() {
		mockArchetypeCorpus("Nutriscore_C");

		var outcome = assess(new ProductIdentification.Identified(PICKED_ARCHETYPE, CLASSIFIED));

		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcome.getType());
		assertEquals(PRODUCT_KEY, outcome.getProductKey());
		assertNull(outcome.getFailureReason());
		assertEquals(CLASSIFIED, outcome.getDiagnostics());
	}

	@Test
	void reportsFailureToIdentifyWithTheReasonAndHowFarTheIdentificationGotWhenItsArchetypeIsNotIdentified() {
		var outcome = assess(new ProductIdentification.NotIdentified(NOT_CLASSIFIED, NEVER_CLASSIFIED));

		assertEquals(ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY, outcome.getType());
		assertEquals(PRODUCT_KEY, outcome.getProductKey());
		assertEquals(NOT_CLASSIFIED, outcome.getFailureReason());
		assertEquals(NEVER_CLASSIFIED, outcome.getDiagnostics());
		assertNull(outcome.getBestScientificAlternative());
	}

	@Test
	void reportsFailureToIdentifyWithoutDiagnosticsWhenTheProductIsUnknown() {
		var outcome = assess(new ProductIdentification.NotIdentified(UNKNOWN_PRODUCT, null));

		assertEquals(ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY, outcome.getType());
		assertEquals(PRODUCT_KEY, outcome.getProductKey());
		assertEquals(UNKNOWN_PRODUCT, outcome.getFailureReason());
		assertNull(outcome.getDiagnostics());
		assertNull(outcome.getBestScientificAlternative());
	}

	@Test
	void reportsTheBestAlternativeFromTheScoredCorpusForTheIdentifiedArchetype() {
		mockArchetypeCorpus("Nutriscore_C");

		var outcome = assess(new ProductIdentification.Identified(PICKED_ARCHETYPE, CLASSIFIED));

		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcome.getType());
		var alternative = outcome.getBestScientificAlternative();
		assertEquals(AlternativeForProductType.SUGGESTION, alternative.getType());
		assertEquals(BETTER_ARCHETYPE, alternative.getArchetype().getId());
		assertEquals("Store brand", alternative.getArchetype().getName(), "graded A where the identified archetype is only graded C");
		assertEquals("Beverages", alternative.getArchetype().getL1Category(), "the recommended archetype's own taxonomy path");
		assertEquals("Juices", alternative.getArchetype().getL2Category());
		assertEquals("Orange juice", alternative.getArchetype().getL3Category());
		assertEquals(0.875, alternative.getReferenceOverallScore(), 1e-9);
		assertEquals(1.0, alternative.getAlternativeOverallScore(), 1e-9);
	}

	@Test
	void reportsTheIdentifiedArchetypeGoodEnoughWhenTheCatalogueDoesNotStockTheBetterOne() {
		mockArchetypeCorpus("Nutriscore_C");

		var outcome = sut.assessOne(PRODUCT_KEY, new ProductIdentification.Identified(PICKED_ARCHETYPE, CLASSIFIED), ScientificWeights.profile(), Set.of())
				.await().atMost(WAIT);

		assertEquals(AlternativeForProductType.GOOD_ENOUGH, outcome.getBestScientificAlternative().getType());
		assertEquals(PICKED_ARCHETYPE, outcome.getBestScientificAlternative().getArchetype().getId());
	}

	@Test
	void reportsNoSuggestionWhenTheIdentifiedArchetypeIsOutsideTheNutriScoreScheme() {
		mockArchetypeCorpus("0");

		var outcome = assess(new ProductIdentification.Identified(PICKED_ARCHETYPE, CLASSIFIED));

		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcome.getType(),
				"the identification succeeded; it is the recommendation that has nothing to say");
		assertEquals(AlternativeForProductType.NO_SUGGESTION, outcome.getBestPersonalAlternative().getType());
		assertEquals(AlternativeForProductType.NO_SUGGESTION, outcome.getBestScientificAlternative().getType());
		assertEquals(AlternativeForProductType.NO_SUGGESTION, outcome.getBestCombinedAlternative().getType());
		assertEquals(CLASSIFIED, outcome.getDiagnostics(), "and how the product was identified is still reported");
	}

	/**
	 * Assess in a Catalogue that stocks both archetypes.
	 */
	private ProductAssessmentOutcome assess(ProductIdentification identification) {
		return sut.assessOne(PRODUCT_KEY, identification, ScientificWeights.profile(), Set.of(PICKED_ARCHETYPE, BETTER_ARCHETYPE)).await().atMost(WAIT);
	}

	/**
	 * A corpus of the two candidate archetypes alone. Their indicator values are all zero, so every dimension has a
	 * zero range and normalises flat, leaving the Nutri-Score the only thing that tells the two apart: the store brand
	 * is graded A and the one the product is only C.
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
}
