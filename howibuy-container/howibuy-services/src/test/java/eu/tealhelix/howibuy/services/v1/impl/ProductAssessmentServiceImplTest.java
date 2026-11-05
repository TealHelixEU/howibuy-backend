package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_OTHER;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.SUCCESS;
import static eu.tealhelix.howibuy.v1.types.RecommendationType.GOOD_ENOUGH;
import static eu.tealhelix.howibuy.v1.types.RecommendationType.RECOMMENDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
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
import eu.tealhelix.common.v1.types.UserId;
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

	private static final UserId USER_ID = new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1");
	private static final User USER = new UserImpl(USER_ID, null, null, false, true, Set.of());
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
		when(singleProductAssessor.assessOne(USER_ID, PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));

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
		when(singleProductAssessor.assessOne(USER_ID, PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(USER_ID, PRODUCT2, UNKNOWN, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT2, FAILURE_TO_IDENTIFY)));

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
		when(singleProductAssessor.assessOne(USER_ID, PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(USER_ID, PRODUCT2, UNKNOWN, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT2, FAILURE_TO_IDENTIFY)));

		var outcomes = sut.assessMultipleProductsSync(USER, CATALOGUE, List.of(PRODUCT, PRODUCT2, PRODUCT), LANGUAGE).await().atMost(WAIT);

		assertEquals(List.of(PRODUCT, PRODUCT2, PRODUCT), outcomes.stream().map(ProductAssessmentOutcome::getProductKey).toList());
	}

	@Test
	void syncBatchIsolatesAnUnexpectedFailureAsFailureOther() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockListedProducts(Set.of(), Map.of());
		mockIdentification(List.of(PRODUCT, PRODUCT2), Map.of(PRODUCT, IDENTIFIED, PRODUCT2, IDENTIFIED));
		when(singleProductAssessor.assessOne(USER_ID, PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(USER_ID, PRODUCT2, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().failure(new RuntimeException("DB down")));

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
		when(singleProductAssessor.assessOne(USER_ID, PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(USER_ID, PRODUCT2, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT2, SUCCESS)));

		sut.assessMultipleProductsSync(USER, CATALOGUE, List.of(PRODUCT, PRODUCT2), LANGUAGE).await().atMost(WAIT);

		verify(personalWeightsProvider, times(1)).forUser(USER);
		verify(referenceProductIdentifier, times(1)).identify(CATALOGUE, Set.of(PRODUCT, PRODUCT2));
		verify(catalogueAssortmentService, times(1)).stockedArchetypes(CATALOGUE);
	}

	@Test
	void eachAlternativeNamesTheProductOfTheCatalogueThatStandsForItsArchetypeInTheLanguageAskedFor() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockIdentification(List.of(PRODUCT), Map.of(PRODUCT, IDENTIFIED));
		when(singleProductAssessor.assessOne(USER_ID, PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(recommending(PRODUCT, KEFIR, AYRAN)));
		mockListedProducts(Set.of(KEFIR.getId(), AYRAN.getId()), Map.of(KEFIR.getId(), KEFIR_PRODUCT, AYRAN.getId(), AYRAN_PRODUCT));

		var outcome = sut.assessSingleProduct(USER, CATALOGUE, PRODUCT, LANGUAGE).await().atMost(WAIT);

		assertEquals(RECOMMENDED, outcome.getRecommendation(), "the rest of the outcome as assessed");
		assertEquals(0.5, outcome.getReferenceOverallScore());
		assertEquals(List.of(KEFIR_PRODUCT, AYRAN_PRODUCT), outcome.getAlternatives().stream().map(AlternativeForProduct::getProduct).toList());
		assertEquals(KEFIR, outcome.getAlternatives().getFirst().getArchetype(), "the rest of the Alternative as recommended");
		assertEquals(0.75, outcome.getAlternatives().getFirst().getOverallScore());
	}

	/**
	 * When the last active Listing of an Alternative's archetype goes between the stocked archetypes being read and the
	 * Listings being read, the Alternative stays, naming no product.
	 */
	@Test
	void anAlternativeWhoseArchetypeHasNoListedProductNamesNone() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockIdentification(List.of(PRODUCT), Map.of(PRODUCT, IDENTIFIED));
		when(singleProductAssessor.assessOne(USER_ID, PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(recommending(PRODUCT, KEFIR)));
		mockListedProducts(Set.of(KEFIR.getId()), Map.of());

		var outcome = sut.assessSingleProduct(USER, CATALOGUE, PRODUCT, LANGUAGE).await().atMost(WAIT);

		assertEquals(KEFIR, outcome.getAlternatives().getFirst().getArchetype());
		assertNull(outcome.getAlternatives().getFirst().getProduct());
	}

	@Test
	void aBatchReadsTheListedProductsOfTheAlternativesOfAllItsProductsAtOnce() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockIdentification(List.of(PRODUCT, PRODUCT2), Map.of(PRODUCT, IDENTIFIED, PRODUCT2, IDENTIFIED));
		when(singleProductAssessor.assessOne(USER_ID, PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(recommending(PRODUCT, KEFIR)));
		when(singleProductAssessor.assessOne(USER_ID, PRODUCT2, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(goodEnough(PRODUCT2)));
		mockListedProducts(Set.of(KEFIR.getId()), Map.of(KEFIR.getId(), KEFIR_PRODUCT));

		var outcomes = sut.assessMultipleProductsSync(USER, CATALOGUE, List.of(PRODUCT, PRODUCT2, PRODUCT), LANGUAGE).await().atMost(WAIT);

		assertEquals(KEFIR_PRODUCT, outcomes.get(0).getAlternatives().getFirst().getProduct());
		assertEquals(GOOD_ENOUGH, outcomes.get(1).getRecommendation());
		assertTrue(outcomes.get(1).getAlternatives().isEmpty(), "good enough, nothing to buy instead");
		assertEquals(KEFIR_PRODUCT, outcomes.get(2).getAlternatives().getFirst().getProduct());
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

	private static ProductAssessmentOutcome recommending(ProductKey productKey, RecommendedArchetype... archetypes) {
		return ImmutableProductAssessmentOutcome.builder()
				.productKey(productKey)
				.type(SUCCESS)
				.recommendation(RECOMMENDED)
				.referenceOverallScore(0.5)
				.alternatives(Stream.of(archetypes).map(ProductAssessmentServiceImplTest::alternative).toList())
				.build();
	}

	private static ProductAssessmentOutcome goodEnough(ProductKey productKey) {
		return ImmutableProductAssessmentOutcome.builder()
				.productKey(productKey)
				.type(SUCCESS)
				.recommendation(GOOD_ENOUGH)
				.referenceOverallScore(0.9)
				.build();
	}

	private static AlternativeForProduct alternative(RecommendedArchetype archetype) {
		return ImmutableAlternativeForProduct.builder()
				.archetype(archetype)
				.overallScore(0.75)
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
