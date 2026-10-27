package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_OTHER;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
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
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.IdentificationFailureReason;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
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
	private static final ProductKey PRODUCT = new ProductKeyImpl("product-key");
	private static final ProductKey PRODUCT2 = new ProductKeyImpl("product-key-2");
	/** Which identifications they are does not matter here, only that each product is assessed with its own. */
	private static final ProductIdentification IDENTIFIED = new ProductIdentification.Identified(new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000fc"), null);
	private static final ProductIdentification UNKNOWN = new ProductIdentification.NotIdentified(IdentificationFailureReason.UNKNOWN_PRODUCT, null);
	/** Which archetypes they are does not matter here, only that every product is assessed against the Catalogue's. */
	private static final Set<ArchetypeProductId> STOCKED = Set.of(new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000fd"));

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
		mockIdentification(List.of(PRODUCT), Map.of(PRODUCT, IDENTIFIED));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));

		var outcome = sut.assessSingleProduct(USER, CATALOGUE, PRODUCT).await().atMost(WAIT);

		assertEquals(PRODUCT, outcome.getProductKey());
		assertEquals(SUCCESS, outcome.getType());
	}

	@Test
	void assessSingleProductRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.assessSingleProduct(SERVICE_USER, CATALOGUE, PRODUCT));
	}

	@Test
	void assessMultipleProductsSyncRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.assessMultipleProductsSync(SERVICE_USER, CATALOGUE, List.of(PRODUCT)));
	}

	@Test
	void assessMultipleProductsAsyncRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.assessMultipleProductsAsync(SERVICE_USER, CATALOGUE, List.of(PRODUCT)));
	}

	@Test
	void syncBatchReportsOneOutcomePerProductInOrder() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockIdentification(List.of(PRODUCT, PRODUCT2), Map.of(PRODUCT, IDENTIFIED, PRODUCT2, UNKNOWN));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(PRODUCT2, UNKNOWN, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT2, FAILURE_TO_IDENTIFY)));

		var outcomes = sut.assessMultipleProductsSync(USER, CATALOGUE, List.of(PRODUCT, PRODUCT2)).await().atMost(WAIT);

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
		mockIdentification(List.of(PRODUCT, PRODUCT2), Map.of(PRODUCT, IDENTIFIED, PRODUCT2, UNKNOWN));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(PRODUCT2, UNKNOWN, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT2, FAILURE_TO_IDENTIFY)));

		var outcomes = sut.assessMultipleProductsSync(USER, CATALOGUE, List.of(PRODUCT, PRODUCT2, PRODUCT)).await().atMost(WAIT);

		assertEquals(List.of(PRODUCT, PRODUCT2, PRODUCT), outcomes.stream().map(ProductAssessmentOutcome::getProductKey).toList());
	}

	@Test
	void syncBatchIsolatesAnUnexpectedFailureAsFailureOther() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockIdentification(List.of(PRODUCT, PRODUCT2), Map.of(PRODUCT, IDENTIFIED, PRODUCT2, IDENTIFIED));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(PRODUCT2, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().failure(new RuntimeException("DB down")));

		var outcomes = sut.assessMultipleProductsSync(USER, CATALOGUE, List.of(PRODUCT, PRODUCT2)).await().atMost(WAIT);

		assertEquals(2, outcomes.size());
		assertEquals(SUCCESS, outcomes.get(0).getType());
		assertEquals(PRODUCT2, outcomes.get(1).getProductKey());
		assertEquals(FAILURE_OTHER, outcomes.get(1).getType());
	}

	@Test
	void asyncBatchStreamsOneOutcomePerProductIsolatingFailures() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockIdentification(List.of(PRODUCT, PRODUCT2), Map.of(PRODUCT, IDENTIFIED, PRODUCT2, IDENTIFIED));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(PRODUCT2, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().failure(new RuntimeException("DB down")));

		var outcomes = sut.assessMultipleProductsAsync(USER, CATALOGUE, List.of(PRODUCT, PRODUCT2))
				.subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE))
				.awaitCompletion(WAIT)
				.getItems();

		assertEquals(2, outcomes.size());
		assertEquals(SUCCESS, outcomes.get(0).getType());
		assertEquals(FAILURE_OTHER, outcomes.get(1).getType());
	}

	@Test
	void resolvesTheUsersWeightingProfileIdentifiesTheProductsAndReadsTheStockedArchetypesOncePerBatchRatherThanOncePerProduct() {
		mockPersonalWeights();
		mockStockedArchetypes();
		mockIdentification(List.of(PRODUCT, PRODUCT2), Map.of(PRODUCT, IDENTIFIED, PRODUCT2, IDENTIFIED));
		when(singleProductAssessor.assessOne(PRODUCT, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(PRODUCT2, IDENTIFIED, PERSONAL, STOCKED)).thenReturn(Uni.createFrom().item(outcome(PRODUCT2, SUCCESS)));

		sut.assessMultipleProductsSync(USER, CATALOGUE, List.of(PRODUCT, PRODUCT2)).await().atMost(WAIT);

		verify(personalWeightsProvider, times(1)).forUser(USER);
		verify(referenceProductIdentifier, times(1)).identify(CATALOGUE, Set.of(PRODUCT, PRODUCT2));
		verify(catalogueAssortmentService, times(1)).stockedArchetypes(CATALOGUE);
	}

	private void mockPersonalWeights() {
		when(personalWeightsProvider.forUser(USER)).thenReturn(Uni.createFrom().item(PERSONAL));
	}

	private void mockStockedArchetypes() {
		when(catalogueAssortmentService.stockedArchetypes(CATALOGUE)).thenReturn(Uni.createFrom().item(STOCKED));
	}

	private void mockIdentification(List<ProductKey> productKeys, Map<ProductKey, ProductIdentification> identifications) {
		when(referenceProductIdentifier.identify(CATALOGUE, Set.copyOf(productKeys))).thenReturn(Uni.createFrom().item(identifications));
	}

	private static ProductAssessmentOutcome outcome(ProductKey productKey, ProductAssessmentOutcomeType type) {
		return ImmutableProductAssessmentOutcome.builder()
				.productKey(productKey)
				.type(type)
				.build();
	}
}
