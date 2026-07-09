package eu.tealhelix.howibuy.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.ArchetypeCategoryDao;
import eu.tealhelix.howibuy.dao.ArchetypeProductDao;
import eu.tealhelix.howibuy.services.model.ArchetypeCategory;
import eu.tealhelix.howibuy.services.model.ArchetypeProduct;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeCategory;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeProduct;
import eu.tealhelix.howibuy.services.v1.ai.ProductAssessmentAiFacade;
import eu.tealhelix.howibuy.services.v1.authz.impl.HowiBuyAuthorizationImpl;
import eu.tealhelix.howibuy.v1.model.ImmutableProductData;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductData;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.impl.ProductKeyImpl;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@EnableAutoWeld
@AddBeanClasses(HowiBuyAuthorizationImpl.class)
@ExtendWith(MockitoExtension.class)
public class ProductAssessmentServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);
	private static final String NO_MATCH = "NONE";

	private static final User USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, false);
	private static final ProductData PRODUCT = ImmutableProductData.builder()
			.productKey(new ProductKeyImpl("product-key"))
			.language(Locale.ENGLISH)
			.name("Freshly squeezed orange juice")
			.price(new BigDecimal("2.50"))
			.currency(Currency.getInstance("EUR"))
			.build();
	private static final ProductData PRODUCT2 = ImmutableProductData.builder()
			.productKey(new ProductKeyImpl("product-key-2"))
			.language(Locale.ENGLISH)
			.name("Some other drink")
			.price(new BigDecimal("1.00"))
			.currency(Currency.getInstance("EUR"))
			.build();

	private static final UUID L1_BEVERAGES = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID L2_JUICES = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID L3_ORANGE = UUID.fromString("00000000-0000-0000-0000-000000000003");

	private static final List<ArchetypeCategory> L1_CATEGORIES = List.of(
			category(L1_BEVERAGES, "Beverages"), category(UUID.fromString("00000000-0000-0000-0000-0000000000ff"), "Dairy"));
	private static final List<ArchetypeCategory> L2_CATEGORIES = List.of(
			category(L2_JUICES, "Juices"), category(UUID.fromString("00000000-0000-0000-0000-0000000000fe"), "Other"));
	private static final List<ArchetypeCategory> L3_CATEGORIES = List.of(
			category(L3_ORANGE, "Orange juice"), category(UUID.fromString("00000000-0000-0000-0000-0000000000fd"), "Apple juice"));
	private static final List<ArchetypeProduct> PRODUCTS = List.of(
			product(UUID.fromString("00000000-0000-0000-0000-0000000000fc"), "Tropicana"),
			product(UUID.fromString("00000000-0000-0000-0000-0000000000fb"), "Store brand"));

	@Produces
	@Mock
	ArchetypeCategoryDao archetypeCategoryDao;

	@Produces
	@Mock
	ArchetypeProductDao archetypeProductDao;

	@Produces
	@Mock
	ProductAssessmentAiFacade productAssessmentAiFacade;

	@Produces
	@RegisterExtension
	private MockReactivePersistenceContextFactory mockPersistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Inject
	ProductAssessmentServiceImpl sut;

	@Test
	void descendsAllFourLevelsAndReportsTheMatchedPath() {
		mockL1Categories();
		mockExtractL1("Beverages");
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory("Juices", "Orange juice");
		mockSubcategoriesOf(L2_JUICES, L3_CATEGORIES);
		mockProductsOf(L3_ORANGE);
		mockExtractArchetypeProduct("Tropicana");

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcome.getType());
		assertEquals(PRODUCT.getProductKey(), outcome.getProductKey());
		assertEquals("Beverages", outcome.getDiagnostics().getL1Category());
		assertEquals("Juices", outcome.getDiagnostics().getL2Category());
		assertEquals("Orange juice", outcome.getDiagnostics().getL3Category());
		assertEquals("Tropicana", outcome.getDiagnostics().getProduct());
	}

	@Test
	void reportsFailureToIdentifyWhenAiFindsNoSubcategory() {
		mockL1Categories();
		mockExtractL1("Beverages");
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory(NO_MATCH, null);

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY, outcome.getType());
		assertEquals("Beverages", outcome.getDiagnostics().getL1Category());
		assertNull(outcome.getDiagnostics().getL2Category());
		assertNull(outcome.getDiagnostics().getL3Category());
		assertNull(outcome.getDiagnostics().getProduct());
	}

	@Test
	void reportsFailureToIdentifyWhenAiFindsNoArchetypeProduct() {
		mockL1Categories();
		mockExtractL1("Beverages");
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory("Juices", "Orange juice");
		mockSubcategoriesOf(L2_JUICES, L3_CATEGORIES);
		mockProductsOf(L3_ORANGE);
		mockExtractArchetypeProduct(NO_MATCH);

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY, outcome.getType());
		assertEquals("Beverages", outcome.getDiagnostics().getL1Category());
		assertEquals("Juices", outcome.getDiagnostics().getL2Category());
		assertEquals("Orange juice", outcome.getDiagnostics().getL3Category());
		assertNull(outcome.getDiagnostics().getProduct());
	}

	@Test
	void reportsFailureOtherWhenAiPicksACategoryOutsideTheCandidates() {
		mockL1Categories();
		mockExtractL1("Confectionery");

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.FAILURE_OTHER, outcome.getType());
	}

	@Test
	void reportsFailureOtherWhenAiPicksAProductOutsideTheCandidates() {
		mockL1Categories();
		mockExtractL1("Beverages");
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory("Juices", "Orange juice");
		mockSubcategoriesOf(L2_JUICES, L3_CATEGORIES);
		mockProductsOf(L3_ORANGE);
		mockExtractArchetypeProduct("A product the AI made up");

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.FAILURE_OTHER, outcome.getType());
		assertEquals("Beverages", outcome.getDiagnostics().getL1Category());
		assertEquals("Juices", outcome.getDiagnostics().getL2Category());
		assertEquals("Orange juice", outcome.getDiagnostics().getL3Category());
		assertNull(outcome.getDiagnostics().getProduct());
	}

	@Test
	void syncBatchReportsAnOutcomePerProductIsolatingFailures() {
		mockL1Categories();
		mockExtractL1("Beverages", "Confectionery");
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory("Juices", "Orange juice");
		mockSubcategoriesOf(L2_JUICES, L3_CATEGORIES);
		mockProductsOf(L3_ORANGE);
		mockExtractArchetypeProduct("Tropicana");

		var outcomes = sut.assessMultipleProductsSync(USER, List.of(PRODUCT, PRODUCT2)).await().atMost(WAIT);

		assertEquals(2, outcomes.size());
		assertEquals(PRODUCT.getProductKey(), outcomes.get(0).getProductKey());
		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcomes.get(0).getType());
		assertEquals(PRODUCT2.getProductKey(), outcomes.get(1).getProductKey());
		assertEquals(ProductAssessmentOutcomeType.FAILURE_OTHER, outcomes.get(1).getType());
	}

	@Test
	void syncBatchIsolatesAnUnexpectedFailureAsFailureOther() {
		when(archetypeCategoryDao.retrieveL1Categories(any()))
				.thenReturn(Uni.createFrom().item(L1_CATEGORIES))
				.thenReturn(Uni.createFrom().failure(new RuntimeException("DB down")));
		mockExtractL1("Beverages");
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory("Juices", "Orange juice");
		mockSubcategoriesOf(L2_JUICES, L3_CATEGORIES);
		mockProductsOf(L3_ORANGE);
		mockExtractArchetypeProduct("Tropicana");

		var outcomes = sut.assessMultipleProductsSync(USER, List.of(PRODUCT, PRODUCT2)).await().atMost(WAIT);

		assertEquals(2, outcomes.size());
		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcomes.get(0).getType());
		assertEquals(ProductAssessmentOutcomeType.FAILURE_OTHER, outcomes.get(1).getType());
	}

	@Test
	void asyncBatchStreamsAnOutcomePerProductIsolatingFailures() {
		mockL1Categories();
		mockExtractL1("Beverages", "Confectionery");
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory("Juices", "Orange juice");
		mockSubcategoriesOf(L2_JUICES, L3_CATEGORIES);
		mockProductsOf(L3_ORANGE);
		mockExtractArchetypeProduct("Tropicana");

		var outcomes = sut.assessMultipleProductsAsync(USER, List.of(PRODUCT, PRODUCT2))
				.subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE))
				.awaitCompletion(WAIT)
				.getItems();

		assertEquals(2, outcomes.size());
		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcomes.get(0).getType());
		assertEquals(ProductAssessmentOutcomeType.FAILURE_OTHER, outcomes.get(1).getType());
	}

	private ProductAssessmentOutcome assess() {
		return sut.assessSingleProduct(USER, PRODUCT).await().atMost(WAIT);
	}

	private void mockL1Categories() {
		when(archetypeCategoryDao.retrieveL1Categories(any())).thenReturn(Uni.createFrom().item(L1_CATEGORIES));
	}

	private void mockSubcategoriesOf(UUID parentId, List<ArchetypeCategory> subcategories) {
		when(archetypeCategoryDao.retrieveSubcategories(any(), eq(parentId))).thenReturn(Uni.createFrom().item(subcategories));
	}

	private void mockProductsOf(UUID categoryId) {
		when(archetypeProductDao.retrieveProductsInCategory(any(), eq(categoryId))).thenReturn(Uni.createFrom().item(PRODUCTS));
	}

	private void mockExtractL1(String... picks) {
		var stubbing = when(productAssessmentAiFacade.extractL1Category(any(), any()));
		for (String pick : picks) {
			stubbing = stubbing.thenReturn(Uni.createFrom().item(pick));
		}
	}

	private void mockExtractSubcategory(String pick1, String pick2) {
		when(productAssessmentAiFacade.extractSubcategory(any(), any()))
				.thenReturn(Uni.createFrom().item(pick1))
				.thenReturn(Uni.createFrom().item(pick2));
	}

	private void mockExtractArchetypeProduct(String pick) {
		when(productAssessmentAiFacade.extractArchetypeProduct(any(), any())).thenReturn(Uni.createFrom().item(pick));
	}

	private static ArchetypeCategory category(UUID id, String name) {
		return ImmutableArchetypeCategory.builder().id(id).name(name).build();
	}

	private static ArchetypeProduct product(UUID id, String name) {
		return ImmutableArchetypeProduct.builder().id(id).name(name).build();
	}
}
