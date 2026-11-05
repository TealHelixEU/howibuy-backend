package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.IdentificationFailureReason.NOT_CLASSIFIED;
import static eu.tealhelix.howibuy.v1.types.IdentificationFailureReason.UNKNOWN_PRODUCT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.services.model.ArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableSubstitutability;
import eu.tealhelix.howibuy.services.model.Substitutability;
import eu.tealhelix.howibuy.v1.model.IdentificationDiagnostics;
import eu.tealhelix.howibuy.v1.model.ImmutableIdentificationDiagnostics;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.Classifier;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.RecommendationType;
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

	private static final UserId USER = new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1");
	private static final ProductKey PRODUCT_KEY = new ProductKeyImpl("product-key");

	private static final ArchetypeCategoryId L2_JUICES = new ArchetypeCategoryIdImpl("00000000-0000-0000-0000-000000000002");

	/** The archetype the product is, and three better ones beside it. */
	private static final ArchetypeProductId PICKED_ARCHETYPE = new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000fc");
	private static final ArchetypeProductId BETTER_ARCHETYPE = new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000fb");
	private static final ArchetypeProductId ORGANIC_ARCHETYPE = new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000fa");
	private static final ArchetypeProductId VALUE_ARCHETYPE = new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000f9");
	private static final Set<ArchetypeProductId> ALL_STOCKED = Set.of(PICKED_ARCHETYPE, BETTER_ARCHETYPE, ORGANIC_ARCHETYPE, VALUE_ARCHETYPE);

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
	private static final ProductIdentification IDENTIFIED = new ProductIdentification.Identified(PICKED_ARCHETYPE, CLASSIFIED);

	@Produces
	@Mock
	@ExcludeBean
	ArchetypeCorpus archetypeCorpus;

	@Inject
	SingleProductAssessor sut;

	@Test
	void reportsHowTheProductWasIdentifiedForTheProductKeyWhenItsArchetypeIsIdentified() {
		mockArchetypeCorpus("Nutriscore_C", 10);

		var outcome = assess(USER, IDENTIFIED, ALL_STOCKED);

		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcome.getType());
		assertEquals(PRODUCT_KEY, outcome.getProductKey());
		assertNull(outcome.getFailureReason());
		assertEquals(CLASSIFIED, outcome.getDiagnostics());
	}

	@Test
	void reportsFailureToIdentifyWithTheReasonAndHowFarTheIdentificationGotWhenItsArchetypeIsNotIdentified() {
		var outcome = assess(USER, new ProductIdentification.NotIdentified(NOT_CLASSIFIED, NEVER_CLASSIFIED), ALL_STOCKED);

		assertEquals(ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY, outcome.getType());
		assertEquals(PRODUCT_KEY, outcome.getProductKey());
		assertEquals(NOT_CLASSIFIED, outcome.getFailureReason());
		assertEquals(NEVER_CLASSIFIED, outcome.getDiagnostics());
		assertNull(outcome.getRecommendation());
		assertTrue(outcome.getAlternatives().isEmpty());
	}

	@Test
	void reportsFailureToIdentifyWithoutDiagnosticsWhenTheProductIsUnknown() {
		var outcome = assess(USER, new ProductIdentification.NotIdentified(UNKNOWN_PRODUCT, null), ALL_STOCKED);

		assertEquals(ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY, outcome.getType());
		assertEquals(PRODUCT_KEY, outcome.getProductKey());
		assertEquals(UNKNOWN_PRODUCT, outcome.getFailureReason());
		assertNull(outcome.getDiagnostics());
		assertNull(outcome.getRecommendation());
		assertTrue(outcome.getAlternatives().isEmpty());
	}

	@Test
	void recommendsTheBetterArchetypeTheCatalogueStocks() {
		mockArchetypeCorpus("Nutriscore_C", 10);

		var outcome = assess(USER, IDENTIFIED, Set.of(PICKED_ARCHETYPE, BETTER_ARCHETYPE));

		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcome.getType());
		assertEquals(RecommendationType.RECOMMENDED, outcome.getRecommendation());
		assertEquals(0.75, outcome.getReferenceOverallScore(), 1e-9);
		assertEquals(1, outcome.getAlternatives().size());
		var alternative = outcome.getAlternatives().getFirst();
		assertEquals(BETTER_ARCHETYPE, alternative.getArchetype().getId());
		assertEquals("Store brand", alternative.getArchetype().getName(), "graded A where the identified archetype is only graded C");
		assertEquals("Beverages", alternative.getArchetype().getL1Category(), "the Alternative's own taxonomy path");
		assertEquals("Juices", alternative.getArchetype().getL2Category());
		assertEquals("Orange juice", alternative.getArchetype().getL3Category());
		assertEquals(1.0, alternative.getOverallScore(), 1e-9);
	}

	@Test
	void findsNoAlternativeWhenTheCatalogueStocksNothingBetter() {
		mockArchetypeCorpus("Nutriscore_C", 10);

		var outcome = assess(USER, IDENTIFIED, Set.of());

		assertEquals(RecommendationType.NO_ALTERNATIVE, outcome.getRecommendation());
		assertEquals(0.75, outcome.getReferenceOverallScore(), 1e-9);
		assertTrue(outcome.getAlternatives().isEmpty());
	}

	@Test
	void saysNothingAboutAnIdentifiedArchetypeWithoutANutriScore() {
		mockArchetypeCorpus("0", 0);

		var outcome = assess(USER, IDENTIFIED, ALL_STOCKED);

		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcome.getType(),
				"the identification succeeded; it is the Recommendation that has nothing to say");
		assertEquals(RecommendationType.NO_SCORE, outcome.getRecommendation());
		assertNull(outcome.getReferenceOverallScore());
		assertTrue(outcome.getAlternatives().isEmpty());
		assertEquals(CLASSIFIED, outcome.getDiagnostics(), "and how the product was identified is still reported");
	}

	@Test
	void offersTheSameAlternativeEveryTimeTheSameUserAssessesTheSameProduct() {
		mockArchetypeCorpus("Nutriscore_C", 10);

		var offered = new HashSet<ArchetypeProductId>();
		for (int i = 0; i < 10; i++) {
			offered.add(alternativeOf(assess(USER, IDENTIFIED, ALL_STOCKED)));
		}

		assertEquals(1, offered.size(), "the Pool holds three products, equally likely to be drawn: " + offered);
	}

	@Test
	void offersOtherUsersAlternativesFromAcrossThePool() {
		mockArchetypeCorpus("Nutriscore_C", 10);

		var offered = new HashSet<ArchetypeProductId>();
		for (int i = 0; i < 30; i++) {
			var user = new UserIdImpl(UUID.nameUUIDFromBytes(("user " + i).getBytes(StandardCharsets.UTF_8)).toString());
			offered.add(alternativeOf(assess(user, IDENTIFIED, ALL_STOCKED)));
		}

		assertEquals(Set.of(BETTER_ARCHETYPE, ORGANIC_ARCHETYPE, VALUE_ARCHETYPE), offered, "each product of the Pool is offered to someone");
	}

	/**
	 * The seed decides which Alternative every user is offered for every product, so it is pinned: a change to it is a
	 * change to every user's Alternatives. The expected value was computed outside Java, from the MD5 of
	 * {@code "<user id>/<archetype id>"} laid out as an RFC 4122 version 3 UUID, its two halves XORed.
	 */
	@Test
	void seedsTheDrawFromTheUserAndTheReferenceProductByAFixedHash() {
		assertEquals(-8622693291531925704L, SingleProductAssessor.seedOf(USER, PICKED_ARCHETYPE));
	}

	private ProductAssessmentOutcome assess(UserId user, ProductIdentification identification, Set<ArchetypeProductId> stocked) {
		return sut.assessOne(user, PRODUCT_KEY, identification, ScientificWeights.profile(), stocked).await().atMost(WAIT);
	}

	private static ArchetypeProductId alternativeOf(ProductAssessmentOutcome outcome) {
		assertEquals(RecommendationType.RECOMMENDED, outcome.getRecommendation());
		return outcome.getAlternatives().getFirst().getArchetype().getId();
	}

	/**
	 * A corpus of the archetype the product is and three better ones. Their indicator values are all zero, so every
	 * dimension but health normalises flat, to 1, leaving the Nutri-Score points the only thing that tells them apart.
	 * The three graded A, at 0 points, score 1 on health and overall. The one the product is, graded C at 10 points,
	 * lies beyond the 99th percentile of the points and scores 0 on health, 0.75 overall.
	 */
	private void mockArchetypeCorpus(String pickedProductNutriScore, int pickedProductNutriScorePoints) {
		var corpus = List.of(
				impacts(PICKED_ARCHETYPE, "Tropicana", "agb-tropicana", pickedProductNutriScore, pickedProductNutriScorePoints),
				impacts(BETTER_ARCHETYPE, "Store brand", "agb-store-brand", "Nutriscore_A", 0),
				impacts(ORGANIC_ARCHETYPE, "Organic brand", "agb-organic-brand", "Nutriscore_A", 0),
				impacts(VALUE_ARCHETYPE, "Value brand", "agb-value-brand", "Nutriscore_A", 0));
		List<Substitutability> matrix = List.of(ImmutableSubstitutability.builder()
				.fromCategoryId(L2_JUICES).toCategoryId(L2_JUICES).degree((short) 5).build());
		when(archetypeCorpus.scoredArchetypes()).thenReturn(Uni.createFrom().item(ScoredArchetypes.of(corpus, matrix)));
	}

	private static ArchetypeProductImpacts impacts(ArchetypeProductId id, String name, String agbCode, String nutriScore, int nutriScorePoints) {
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
				.nutriScorePoints(nutriScorePoints)
				.build();
	}
}
