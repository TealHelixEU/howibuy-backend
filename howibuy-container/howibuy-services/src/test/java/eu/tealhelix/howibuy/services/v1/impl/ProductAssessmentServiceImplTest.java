package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_OTHER;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.v1.model.AlternativeForProduct;
import eu.tealhelix.howibuy.v1.model.ImmutableAlternativeForProduct;
import eu.tealhelix.howibuy.v1.model.ImmutableListedProduct;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ImmutableRecommendedArchetype;
import eu.tealhelix.howibuy.v1.model.ListedProduct;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.RecommendedArchetype;
import eu.tealhelix.howibuy.v1.types.AlternativeForProductType;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.IdentificationFailureReason;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import io.smallrye.mutiny.Uni;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.jboss.weld.junit5.auto.ExcludeBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@EnableAutoWeld
@AddBeanClasses(TealHelixAuthorizationImpl.class)
@ExtendWith(MockitoExtension.class)
public class ProductAssessmentServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final User USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, true, Set.of());
	private static final User SERVICE_USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, false, Set.of());
	/** Which profile it is does not matter here, only that the assessor is handed the one the provider returned. */
	private static final WeightProfile PERSONAL = ScientificWeights.profile();

	private static final CatalogueId CATALOGUE = new CatalogueIdImpl("test-gr");
	private static final String LANGUAGE = "en";
	private static final ProductKey PRODUCT = new ProductKeyImpl("product-key");
	private static final ProductKey PRODUCT2 = new ProductKeyImpl("product-key-2");
	/** Which identifications they are does not matter here, only that each product is assessed with its own. */
	private static final ProductIdentification IDENTIFIED = new ProductIdentification.Identified(new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000fc"), null);
	private static final ProductIdentification UNKNOWN = new ProductIdentification.NotIdentified(IdentificationFailureReason.UNKNOWN_PRODUCT, null);
	/** Which archetypes they are does not matter here, only that every product is assessed against the Catalogue's. */
	private static final Set<ArchetypeProductId> STOCKED = Set.of(new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000fd"));
	private static final RecommendedArchetype KEFIR = recommendedArchetype("00000000-0000-0000-0000-0000000000a1", "Kefir");
	private static final RecommendedArchetype AYRAN = recommendedArchetype("00000000-0000-0000-0000-0000000000a2", "Ayran");
	private static final RecommendedArchetype YOGHURT = recommendedArchetype("00000000-0000-0000-0000-0000000000a3", "Yoghurt");
	private static final ListedProduct KEFIR_PRODUCT = listedProduct("kefir-key", "Kefir");
	private static final ListedProduct AYRAN_PRODUCT = listedProduct("ayran-key", "Ayran");

	@Produces
	@ExcludeBean
	@Mock
	ReferenceProductIdentifier referenceProductIdentifier;

	@Produces
	@ExcludeBean
	@Mock
	SingleProductAssessor singleProductAssessor;

	@Produces
	@ExcludeBean
	@Mock
	PersonalWeightsProvider personalWeightsProvider;

	@Produces
	@ExcludeBean
	@Mock
	CatalogueAssortmentService catalogueAssortmentService;

	@Inject
	ProductAssessmentServiceImpl sut;

	@Test
	void assessSingleProductReturnsTheAssessorsOutcomeOfTheIdentifiedProductForAValidUser() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockListedProducts(Set.of(), Map.of());
		mockIdentification(List.of(PRODUCT), Map.of(PRODUCT, IDENTIFIED));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));

		var outcome = sut.assessSingleProduct(USER, CATALOGUE, PRODUCT, LANGUAGE).await().atMost(WAIT);

		assertEquals(PRODUCT, outcome.getProductKey());
		assertEquals(SUCCESS, outcome.getType());
	}

	@Test
	void assessSingleProductRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.assessSingleProduct(SERVICE_USER, CATALOGUE, PRODUCT, LANGUAGE));
	}

	@Test
	void assessMultipleProductsSyncRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.assessMultipleProductsSync(SERVICE_USER, CATALOGUE, List.of(PRODUCT), LANGUAGE));
	}

	@Test
	void syncBatchReportsOneOutcomePerProductInOrder() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockListedProducts(Set.of(), Map.of());
		mockIdentification(List.of(PRODUCT, PRODUCT2), Map.of(PRODUCT, IDENTIFIED, PRODUCT2, UNKNOWN));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(PRODUCT2, UNKNOWN, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT2, FAILURE_TO_IDENTIFY)));

		var outcomes = sut.assessMultipleProductsSync(USER, CATALOGUE, List.of(PRODUCT, PRODUCT2), LANGUAGE).await().atMost(WAIT);

		assertEquals(2, outcomes.size());
		assertEquals(PRODUCT, outcomes.get(0).getProductKey());
		assertEquals(SUCCESS, outcomes.get(0).getType());
		assertEquals(PRODUCT2, outcomes.get(1).getProductKey());
		assertEquals(FAILURE_TO_IDENTIFY, outcomes.get(1).getType());
	}

	/**
	 * The products are identified once each, but every key sent is answered.
	 */
	@Test
	void syncBatchReportsAnOutcomeForEachTimeAProductIsSent() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockListedProducts(Set.of(), Map.of());
		mockIdentification(List.of(PRODUCT, PRODUCT2), Map.of(PRODUCT, IDENTIFIED, PRODUCT2, UNKNOWN));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(PRODUCT2, UNKNOWN, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT2, FAILURE_TO_IDENTIFY)));

		var outcomes = sut.assessMultipleProductsSync(USER, CATALOGUE, List.of(PRODUCT, PRODUCT2, PRODUCT), LANGUAGE).await().atMost(WAIT);

		assertEquals(List.of(PRODUCT, PRODUCT2, PRODUCT), outcomes.stream().map(ProductAssessmentOutcome::getProductKey).toList());
	}

	@Test
	void syncBatchIsolatesAnUnexpectedFailureAsFailureOther() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockListedProducts(Set.of(), Map.of());
		mockIdentification(List.of(PRODUCT, PRODUCT2), Map.of(PRODUCT, IDENTIFIED, PRODUCT2, IDENTIFIED));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(PRODUCT2, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().failure(new RuntimeException("DB down")));

		var outcomes = sut.assessMultipleProductsSync(USER, CATALOGUE, List.of(PRODUCT, PRODUCT2), LANGUAGE).await().atMost(WAIT);

		assertEquals(2, outcomes.size());
		assertEquals(SUCCESS, outcomes.get(0).getType());
		assertEquals(PRODUCT2, outcomes.get(1).getProductKey());
		assertEquals(FAILURE_OTHER, outcomes.get(1).getType());
	}

	@Test
	void resolvesTheUsersWeightingProfileIdentifiesTheProductsAndReadsTheStockedArchetypesOncePerBatchRatherThanOncePerProduct() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockListedProducts(Set.of(), Map.of());
		mockIdentification(List.of(PRODUCT, PRODUCT2), Map.of(PRODUCT, IDENTIFIED, PRODUCT2, IDENTIFIED));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(PRODUCT2, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT2, SUCCESS)));

		sut.assessMultipleProductsSync(USER, CATALOGUE, List.of(PRODUCT, PRODUCT2), LANGUAGE).await().atMost(WAIT);

		verify(personalWeightsProvider, times(1)).forUser(USER);
		verify(referenceProductIdentifier, times(1)).identify(CATALOGUE, Set.of(PRODUCT, PRODUCT2));
		verify(catalogueAssortmentService, times(1)).stockedArchetypes(CATALOGUE);
	}

	@Test
	void eachSuggestionNamesTheProductOfTheCatalogueThatStandsForItsArchetypeInTheLanguageAskedFor() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockIdentification(List.of(PRODUCT), Map.of(PRODUCT, IDENTIFIED));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(
				assessed(PRODUCT, suggestion(KEFIR), goodEnough(YOGHURT), suggestion(AYRAN))));
		mockListedProducts(Set.of(KEFIR.getId(), AYRAN.getId()), Map.of(KEFIR.getId(), KEFIR_PRODUCT, AYRAN.getId(), AYRAN_PRODUCT));

		var outcome = sut.assessSingleProduct(USER, CATALOGUE, PRODUCT, LANGUAGE).await().atMost(WAIT);

		assertEquals(KEFIR_PRODUCT, outcome.getBestPersonalAlternative().getProduct());
		assertEquals(KEFIR, outcome.getBestPersonalAlternative().getArchetype(), "the rest of the alternative as ranked");
		assertNull(outcome.getBestScientificAlternative().getProduct(), "the scanned product is good enough, nothing to buy instead");
		assertEquals(AYRAN_PRODUCT, outcome.getBestCombinedAlternative().getProduct());
	}

	/**
	 * When the last active Listing of a recommended archetype goes between the stocked archetypes being read and the
	 * Listings being read, the suggestion stays, naming no product.
	 */
	@Test
	void aSuggestionWhoseArchetypeHasNoListedProductNamesNone() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockIdentification(List.of(PRODUCT), Map.of(PRODUCT, IDENTIFIED));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(
				assessed(PRODUCT, suggestion(KEFIR), suggestion(KEFIR), suggestion(KEFIR))));
		mockListedProducts(Set.of(KEFIR.getId()), Map.of());

		var outcome = sut.assessSingleProduct(USER, CATALOGUE, PRODUCT, LANGUAGE).await().atMost(WAIT);

		assertEquals(SUGGESTION, outcome.getBestPersonalAlternative().getType());
		assertNull(outcome.getBestPersonalAlternative().getProduct());
	}

	@Test
	void aBatchReadsTheListedProductsOfTheSuggestionsOfAllItsProductsAtOnce() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockIdentification(List.of(PRODUCT, PRODUCT2), Map.of(PRODUCT, IDENTIFIED, PRODUCT2, UNKNOWN));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(
				assessed(PRODUCT, suggestion(KEFIR), goodEnough(YOGHURT), suggestion(KEFIR))));
		when(singleProductAssessor.assessOne(PRODUCT2, UNKNOWN, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT2, FAILURE_TO_IDENTIFY)));
		mockListedProducts(Set.of(KEFIR.getId()), Map.of(KEFIR.getId(), KEFIR_PRODUCT));

		var outcomes = sut.assessMultipleProductsSync(USER, CATALOGUE, List.of(PRODUCT, PRODUCT2, PRODUCT), LANGUAGE).await().atMost(WAIT);

		assertEquals(KEFIR_PRODUCT, outcomes.get(0).getBestPersonalAlternative().getProduct());
		assertEquals(FAILURE_TO_IDENTIFY, outcomes.get(1).getType());
		assertEquals(KEFIR_PRODUCT, outcomes.get(2).getBestCombinedAlternative().getProduct());
		verify(catalogueAssortmentService, times(1)).listedProducts(CATALOGUE, Set.of(KEFIR.getId()), LANGUAGE);
	}

	private void mockPersonalWeights() {
		when(personalWeightsProvider.forUser(USER)).thenReturn(Uni.createFrom().item(new WeightProfileAndAttemptId(PERSONAL, null)));
	}

	private void mockStockedArchetypes() {
		when(catalogueAssortmentService.stockedArchetypes(CATALOGUE)).thenReturn(Uni.createFrom().item(STOCKED));
	}

	private void mockListedProducts(Set<ArchetypeProductId> archetypeIds, Map<ArchetypeProductId, ListedProduct> products) {
		when(catalogueAssortmentService.listedProducts(CATALOGUE, archetypeIds, LANGUAGE)).thenReturn(Uni.createFrom().item(products));
	}

	private void mockIdentification(List<ProductKey> productKeys, Map<ProductKey, ProductIdentification> identifications) {
		when(referenceProductIdentifier.identify(CATALOGUE, Set.copyOf(productKeys))).thenReturn(Uni.createFrom().item(identifications));
	}

	private static ProductAssessmentOutcome assessed(ProductKey productKey, AlternativeForProduct personal, AlternativeForProduct scientific, AlternativeForProduct combined) {
		return ImmutableProductAssessmentOutcome.builder()
				.productKey(productKey)
				.type(SUCCESS)
				.bestPersonalAlternative(personal)
				.bestScientificAlternative(scientific)
				.bestCombinedAlternative(combined)
				.build();
	}

	private static AlternativeForProduct suggestion(RecommendedArchetype archetype) {
		return alternative(AlternativeForProductType.SUGGESTION, archetype);
	}

	private static AlternativeForProduct goodEnough(RecommendedArchetype archetype) {
		return alternative(AlternativeForProductType.GOOD_ENOUGH, archetype);
	}

	private static AlternativeForProduct alternative(AlternativeForProductType type, RecommendedArchetype archetype) {
		return ImmutableAlternativeForProduct.builder()
				.type(type)
				.archetype(archetype)
				.referenceOverallScore(0.5)
				.alternativeOverallScore(0.75)
				.build();
	}

	private static RecommendedArchetype recommendedArchetype(String id, String name) {
		return ImmutableRecommendedArchetype.builder()
				.id(new ArchetypeProductIdImpl(id))
				.name(name)
				.l1Category("Dairy")
				.l2Category("Fermented milk")
				.l3Category("Drinks")
				.build();
	}

	private static ListedProduct listedProduct(String productKey, String name) {
		return ImmutableListedProduct.builder()
				.productKey(new ProductKeyImpl(productKey))
				.name(name)
				.language(LANGUAGE)
				.price(new BigDecimal("2.49"))
				.currency(Currency.getInstance("EUR"))
				.build();
	}

	private static ProductAssessmentOutcome outcome(ProductKey productKey, ProductAssessmentOutcomeType type) {
		return ImmutableProductAssessmentOutcome.builder()
				.productKey(productKey)
				.type(type)
				.build();
	}
}
