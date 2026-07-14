package eu.tealhelix.howibuy.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import eu.tealhelix.howibuy.dao.ArchetypeCategoryDao;
import eu.tealhelix.howibuy.dao.ArchetypeProductDao;
import eu.tealhelix.howibuy.services.model.ArchetypeCategory;
import eu.tealhelix.howibuy.services.model.ArchetypeProduct;
import eu.tealhelix.howibuy.services.model.FoodTerm;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeCategory;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeProduct;
import eu.tealhelix.howibuy.services.model.ImmutableFoodTerm;
import eu.tealhelix.howibuy.services.v1.ai.AiSelection;
import eu.tealhelix.howibuy.services.v1.ai.ProductAssessmentAiFacade;
import eu.tealhelix.howibuy.services.v1.enrichment.FoodTermGlossary;
import eu.tealhelix.howibuy.v1.model.ImmutableProductData;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductData;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.impl.ProductKeyImpl;
import io.smallrye.mutiny.Uni;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.jboss.weld.junit5.auto.ExcludeBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@EnableAutoWeld
@AddBeanClasses(SingleProductAssessor.class)
@ExtendWith(MockitoExtension.class)
public class SingleProductAssessorTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	// At every level the intended pick is the first candidate (index 0) in the lists below.
	private static final AiSelection FIRST = new AiSelection.Match(0);

	private static final ProductData PRODUCT = ImmutableProductData.builder()
			.productKey(new ProductKeyImpl("product-key"))
			.language(Locale.ENGLISH)
			.name("Freshly squeezed orange juice")
			.price(new BigDecimal("2.50"))
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
	@Mock
	@ExcludeBean
	FoodTermGlossary foodTermGlossary;

	@Produces
	@RegisterExtension
	private MockReactivePersistenceContextFactory mockPersistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Inject
	SingleProductAssessor sut;

	@Test
	void descendsAllFourLevelsAndReportsTheMatchedPath() {
		mockGlossary(List.of());
		mockL1Categories();
		mockExtractL1(FIRST);
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory(FIRST, FIRST);
		mockSubcategoriesOf(L2_JUICES, L3_CATEGORIES);
		mockProductsOf(L3_ORANGE);
		mockExtractArchetypeProduct(FIRST);

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcome.getType());
		assertEquals(PRODUCT.getProductKey(), outcome.getProductKey());
		assertEquals("Beverages", outcome.getDiagnostics().getL1Category());
		assertEquals("Juices", outcome.getDiagnostics().getL2Category());
		assertEquals("Orange juice", outcome.getDiagnostics().getL3Category());
		assertEquals("Tropicana", outcome.getDiagnostics().getProduct());
	}

	@Test
	void feedsTheGlossaryMatchesForTheProductLanguageIntoEveryLevel() {
		var vlita = ImmutableFoodTerm.builder()
				.term("Βλήτα").canonicalEn("amaranth greens").description("a leafy green vegetable").build();
		mockGlossary(List.of(vlita));
		mockL1Categories();
		mockExtractL1(FIRST);
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory(FIRST, FIRST);
		mockSubcategoriesOf(L2_JUICES, L3_CATEGORIES);
		mockProductsOf(L3_ORANGE);
		mockExtractArchetypeProduct(FIRST);

		assess();

		verify(foodTermGlossary).match(eq("en"), eq("Freshly squeezed orange juice"));
		verify(productAssessmentAiFacade).extractL1Category(any(), any(), eq(List.of(vlita)));
		verify(productAssessmentAiFacade, times(2)).extractSubcategory(any(), any(), eq(List.of(vlita)));
		verify(productAssessmentAiFacade).extractArchetypeProduct(any(), any(), eq(List.of(vlita)));
	}

	@Test
	void reportsFailureToIdentifyWhenAiFindsNoSubcategory() {
		mockGlossary(List.of());
		mockL1Categories();
		mockExtractL1(FIRST);
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory(new AiSelection.None());

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY, outcome.getType());
		assertEquals("Beverages", outcome.getDiagnostics().getL1Category());
		assertNull(outcome.getDiagnostics().getL2Category());
		assertNull(outcome.getDiagnostics().getL3Category());
		assertNull(outcome.getDiagnostics().getProduct());
	}

	@Test
	void reportsFailureToIdentifyWhenAiFindsNoArchetypeProduct() {
		mockGlossary(List.of());
		mockL1Categories();
		mockExtractL1(FIRST);
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory(FIRST, FIRST);
		mockSubcategoriesOf(L2_JUICES, L3_CATEGORIES);
		mockProductsOf(L3_ORANGE);
		mockExtractArchetypeProduct(new AiSelection.None());

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY, outcome.getType());
		assertEquals("Beverages", outcome.getDiagnostics().getL1Category());
		assertEquals("Juices", outcome.getDiagnostics().getL2Category());
		assertEquals("Orange juice", outcome.getDiagnostics().getL3Category());
		assertNull(outcome.getDiagnostics().getProduct());
	}

	@Test
	void reportsFailureOtherWhenAiPicksACategoryOutsideTheCandidates() {
		mockGlossary(List.of());
		mockL1Categories();
		mockExtractL1(new AiSelection.Malformed("Confectionery"));

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.FAILURE_OTHER, outcome.getType());
	}

	@Test
	void reportsFailureOtherWhenAiPicksAProductOutsideTheCandidates() {
		mockGlossary(List.of());
		mockL1Categories();
		mockExtractL1(FIRST);
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory(FIRST, FIRST);
		mockSubcategoriesOf(L2_JUICES, L3_CATEGORIES);
		mockProductsOf(L3_ORANGE);
		mockExtractArchetypeProduct(new AiSelection.Malformed("A product the AI made up"));

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.FAILURE_OTHER, outcome.getType());
		assertEquals("Beverages", outcome.getDiagnostics().getL1Category());
		assertEquals("Juices", outcome.getDiagnostics().getL2Category());
		assertEquals("Orange juice", outcome.getDiagnostics().getL3Category());
		assertNull(outcome.getDiagnostics().getProduct());
	}

	@Test
	void resolvesEachCategoryLevelFromTheGlossaryHintWithoutCallingTheAi() {
		var term = ImmutableFoodTerm.builder()
				.term("orange").canonicalEn("orange").description("a citrus fruit")
				.categoryHint("Beverages → Juices → Orange juice")
				.build();
		mockGlossary(List.of(term));
		mockL1Categories();
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockSubcategoriesOf(L2_JUICES, L3_CATEGORIES);
		mockProductsOf(L3_ORANGE);
		mockExtractArchetypeProduct(FIRST);

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcome.getType());
		assertEquals("Beverages", outcome.getDiagnostics().getL1Category());
		assertEquals("Juices", outcome.getDiagnostics().getL2Category());
		assertEquals("Orange juice", outcome.getDiagnostics().getL3Category());
		assertEquals("Tropicana", outcome.getDiagnostics().getProduct());
		verify(productAssessmentAiFacade, never()).extractL1Category(any(), any(), any());
		verify(productAssessmentAiFacade, never()).extractSubcategory(any(), any(), any());
		verify(productAssessmentAiFacade).extractArchetypeProduct(any(), any(), any());
	}

	@Test
	void fallsBackToTheAiForALevelWhereRecognizedTermsHintConflictingCandidates() {
		var juice = ImmutableFoodTerm.builder()
				.term("juice").canonicalEn("juice").description("a drink").categoryHint("Beverages").build();
		var milk = ImmutableFoodTerm.builder()
				.term("milk").canonicalEn("milk").description("a dairy drink").categoryHint("Dairy").build();
		mockGlossary(List.of(juice, milk));
		mockL1Categories();
		mockExtractL1(FIRST);
		mockSubcategoriesOf(L1_BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory(FIRST, FIRST);
		mockSubcategoriesOf(L2_JUICES, L3_CATEGORIES);
		mockProductsOf(L3_ORANGE);
		mockExtractArchetypeProduct(FIRST);

		var outcome = assess();

		assertEquals(ProductAssessmentOutcomeType.SUCCESS, outcome.getType());
		verify(productAssessmentAiFacade).extractL1Category(any(), any(), any());
	}

	private ProductAssessmentOutcome assess() {
		return sut.assessOne(PRODUCT).await().atMost(WAIT);
	}

	private void mockGlossary(List<FoodTerm> matches) {
		when(foodTermGlossary.match(any(), any())).thenReturn(Uni.createFrom().item(matches));
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

	private void mockExtractL1(AiSelection pick) {
		when(productAssessmentAiFacade.extractL1Category(any(), any(), any())).thenReturn(Uni.createFrom().item(pick));
	}

	private void mockExtractSubcategory(AiSelection pick) {
		when(productAssessmentAiFacade.extractSubcategory(any(), any(), any())).thenReturn(Uni.createFrom().item(pick));
	}

	private void mockExtractSubcategory(AiSelection pick1, AiSelection pick2) {
		when(productAssessmentAiFacade.extractSubcategory(any(), any(), any()))
				.thenReturn(Uni.createFrom().item(pick1))
				.thenReturn(Uni.createFrom().item(pick2));
	}

	private void mockExtractArchetypeProduct(AiSelection pick) {
		when(productAssessmentAiFacade.extractArchetypeProduct(any(), any(), any())).thenReturn(Uni.createFrom().item(pick));
	}

	private static ArchetypeCategory category(UUID id, String name) {
		return ImmutableArchetypeCategory.builder().id(id).name(name).build();
	}

	private static ArchetypeProduct product(UUID id, String name) {
		return ImmutableArchetypeProduct.builder().id(id).name(name).build();
	}
}
