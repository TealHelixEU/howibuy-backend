package eu.tealhelix.howibuy.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
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
import eu.tealhelix.howibuy.v1.model.ImmutableMatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.ImmutableProductData;
import eu.tealhelix.howibuy.v1.model.MatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.ProductBaseData;
import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeCategoryIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import io.smallrye.mutiny.Uni;
import org.jboss.logmanager.LogContext;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.jboss.weld.junit5.auto.ExcludeBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@EnableAutoWeld
@AddBeanClasses({ArchetypeMatching.class, ProductClassificationGuidance.class})
@ExtendWith(MockitoExtension.class)
public class ArchetypeMatchingTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	// At every level the intended pick is the first candidate (index 0) in the lists below.
	private static final AiSelection FIRST = new AiSelection.Match(0);

	private static final ProductBaseData PRODUCT = ImmutableProductData.builder()
			.productKey(new ProductKeyImpl("product-key"))
			.language(Locale.ENGLISH)
			.name("Freshly squeezed orange juice")
			.price(new BigDecimal("2.50"))
			.currency(Currency.getInstance("EUR"))
			.build();

	/** The category the AI picks at each level of every successful descent below. */
	private static final ArchetypeCategory BEVERAGES = category("00000000-0000-0000-0000-000000000001", "Beverages");
	private static final ArchetypeCategory JUICES = category("00000000-0000-0000-0000-000000000002", "Juices");
	private static final ArchetypeCategory ORANGE_JUICE = category("00000000-0000-0000-0000-000000000003", "Orange juice");

	private static final List<ArchetypeCategory> L1_CATEGORIES = List.of(
			BEVERAGES, category("00000000-0000-0000-0000-0000000000ff", "Dairy"));
	private static final List<ArchetypeCategory> L2_CATEGORIES = List.of(
			JUICES, category("00000000-0000-0000-0000-0000000000fe", "Other"));
	private static final List<ArchetypeCategory> L3_CATEGORIES = List.of(
			ORANGE_JUICE, category("00000000-0000-0000-0000-0000000000fd", "Apple juice"));
	/** The archetype the AI picks at the last level of every successful descent below. */
	private static final ArchetypeProductId PICKED_ARCHETYPE = new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000fc");

	private static final List<ArchetypeProduct> PRODUCTS = List.of(
			product(PICKED_ARCHETYPE, "Tropicana"),
			product(new ArchetypeProductIdImpl("00000000-0000-0000-0000-0000000000fb"), "Store brand"));

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

	private final List<LogRecord> logged = new ArrayList<>();

	private final Handler logHandler = new Handler() {
		@Override
		public void publish(LogRecord record) {
			logged.add(record);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}
	};

	@Inject
	ArchetypeMatching sut;

	@BeforeEach
	void setUp() {
		var logger = LogContext.getLogContext().getLogger(ArchetypeMatching.class.getName());
		logger.addHandler(logHandler);
		logger.setUseParentHandlers(false);
	}

	@AfterEach
	void tearDown() {
		var logger = LogContext.getLogContext().getLogger(ArchetypeMatching.class.getName());
		logger.removeHandler(logHandler);
		logger.setUseParentHandlers(true);
	}

	@Test
	void descendsAllFourLevelsAndReportsTheMatchedArchetypeWithItsPath() {
		mockGlossary(List.of());
		mockL1Categories();
		mockExtractL1(FIRST);
		mockSubcategoriesOf(BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory(FIRST, FIRST);
		mockSubcategoriesOf(JUICES, L3_CATEGORIES);
		mockProductsOf(ORANGE_JUICE);
		mockExtractArchetypeProduct(FIRST);

		var outcome = match();

		assertEquals(new ArchetypeMatchOutcome.Matched(
				PICKED_ARCHETYPE, diagnostics("Beverages", "Juices", "Orange juice", "Tropicana")), outcome);
	}

	@Test
	void feedsTheGlossaryMatchesForTheProductLanguageIntoEveryLevel() {
		var vlita = ImmutableFoodTerm.builder()
				.term("Βλήτα").canonicalEn("amaranth greens").description("a leafy green vegetable").build();
		mockGlossary(List.of(vlita));
		mockL1Categories();
		mockExtractL1(FIRST);
		mockSubcategoriesOf(BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory(FIRST, FIRST);
		mockSubcategoriesOf(JUICES, L3_CATEGORIES);
		mockProductsOf(ORANGE_JUICE);
		mockExtractArchetypeProduct(FIRST);

		match();

		verify(foodTermGlossary).match(eq("en"), eq("Freshly squeezed orange juice"));
		verify(productAssessmentAiFacade).extractL1Category(eq(PRODUCT), any(), eq(List.of(vlita)));
		verify(productAssessmentAiFacade, times(2)).extractSubcategory(eq(PRODUCT), any(), eq(List.of(vlita)));
		verify(productAssessmentAiFacade).extractArchetypeProduct(eq(PRODUCT), any(), eq(List.of(vlita)), any());
	}

	@Test
	void reportsNoMatchWhenAiFindsNoSubcategory() {
		mockGlossary(List.of());
		mockL1Categories();
		mockExtractL1(FIRST);
		mockSubcategoriesOf(BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory(new AiSelection.None());

		var outcome = match();

		assertEquals(new ArchetypeMatchOutcome.NoMatch(
				diagnostics("Beverages", null, null, null)), outcome);
	}

	@Test
	void reportsNoMatchWhenAiFindsNoArchetypeProduct() {
		mockGlossary(List.of());
		mockL1Categories();
		mockExtractL1(FIRST);
		mockSubcategoriesOf(BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory(FIRST, FIRST);
		mockSubcategoriesOf(JUICES, L3_CATEGORIES);
		mockProductsOf(ORANGE_JUICE);
		mockExtractArchetypeProduct(new AiSelection.None());

		var outcome = match();

		assertEquals(new ArchetypeMatchOutcome.NoMatch(
				diagnostics("Beverages", "Juices", "Orange juice", null)), outcome);
	}

	@Test
	void reportsAndLogsAMalformedReplyWhenAiPicksACategoryOutsideTheCandidates() {
		mockGlossary(List.of());
		mockL1Categories();
		mockExtractL1(new AiSelection.Malformed("Confectionery"));

		var outcome = match();

		assertEquals(new ArchetypeMatchOutcome.Malformed(
				diagnostics(null, null, null, null), "category", "Confectionery"), outcome);
		assertEquals(List.of("AI selection was not a valid candidate number, kind: category, raw reply: 'Confectionery'"), warnings());
	}

	@Test
	void reportsAndLogsAMalformedReplyWhenAiPicksAProductOutsideTheCandidates() {
		mockGlossary(List.of());
		mockL1Categories();
		mockExtractL1(FIRST);
		mockSubcategoriesOf(BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory(FIRST, FIRST);
		mockSubcategoriesOf(JUICES, L3_CATEGORIES);
		mockProductsOf(ORANGE_JUICE);
		mockExtractArchetypeProduct(new AiSelection.Malformed("A product the AI made up"));

		var outcome = match();

		assertEquals(new ArchetypeMatchOutcome.Malformed(
				diagnostics("Beverages", "Juices", "Orange juice", null), "product", "A product the AI made up"), outcome);
		assertEquals(List.of("AI selection was not a valid candidate number, kind: product, raw reply: 'A product the AI made up'"), warnings());
	}

	@Test
	void resolvesEachCategoryLevelFromTheGlossaryHintWithoutCallingTheAi() {
		var term = ImmutableFoodTerm.builder()
				.term("orange").canonicalEn("orange").description("a citrus fruit")
				.categoryHintL1("Beverages").categoryHintL2("Juices").categoryHintL3("Orange juice")
				.build();
		mockGlossary(List.of(term));
		mockL1Categories();
		mockSubcategoriesOf(BEVERAGES, L2_CATEGORIES);
		mockSubcategoriesOf(JUICES, L3_CATEGORIES);
		mockProductsOf(ORANGE_JUICE);
		mockExtractArchetypeProduct(FIRST);

		var outcome = match();

		assertEquals(new ArchetypeMatchOutcome.Matched(
				PICKED_ARCHETYPE, diagnostics("Beverages", "Juices", "Orange juice", "Tropicana")), outcome);
		verify(productAssessmentAiFacade, never()).extractL1Category(any(), any(), any());
		verify(productAssessmentAiFacade, never()).extractSubcategory(any(), any(), any());
		verify(productAssessmentAiFacade).extractArchetypeProduct(any(), any(), any(), any());
	}

	@Test
	void fallsBackToTheAiForALevelWhereRecognizedTermsHintConflictingCandidates() {
		var juice = ImmutableFoodTerm.builder()
				.term("juice").canonicalEn("juice").description("a drink").categoryHintL1("Beverages").build();
		var milk = ImmutableFoodTerm.builder()
				.term("milk").canonicalEn("milk").description("a dairy drink").categoryHintL1("Dairy").build();
		mockGlossary(List.of(juice, milk));
		mockL1Categories();
		mockExtractL1(FIRST);
		mockSubcategoriesOf(BEVERAGES, L2_CATEGORIES);
		mockExtractSubcategory(FIRST, FIRST);
		mockSubcategoriesOf(JUICES, L3_CATEGORIES);
		mockProductsOf(ORANGE_JUICE);
		mockExtractArchetypeProduct(FIRST);

		var outcome = match();

		assertInstanceOf(ArchetypeMatchOutcome.Matched.class, outcome);
		verify(productAssessmentAiFacade).extractL1Category(any(), any(), any());
	}

	private ArchetypeMatchOutcome match() {
		return sut.match(PRODUCT).await().atMost(WAIT);
	}

	private List<String> warnings() {
		return logged.stream().filter(record -> record.getLevel().intValue() == Level.WARNING.intValue()).map(LogRecord::getMessage).toList();
	}

	private void mockGlossary(List<FoodTerm> matches) {
		when(foodTermGlossary.match(any(), any())).thenReturn(Uni.createFrom().item(matches));
	}

	private void mockL1Categories() {
		when(archetypeCategoryDao.retrieveL1Categories(any())).thenReturn(Uni.createFrom().item(L1_CATEGORIES));
	}

	private void mockSubcategoriesOf(ArchetypeCategory parent, List<ArchetypeCategory> subcategories) {
		when(archetypeCategoryDao.retrieveSubcategories(any(), eq(parent))).thenReturn(Uni.createFrom().item(subcategories));
	}

	private void mockProductsOf(ArchetypeCategory category) {
		when(archetypeProductDao.retrieveProductsInCategory(any(), eq(category))).thenReturn(Uni.createFrom().item(PRODUCTS));
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
		when(productAssessmentAiFacade.extractArchetypeProduct(any(), any(), any(), any())).thenReturn(Uni.createFrom().item(pick));
	}

	private static MatchDiagnostics diagnostics(String l1Category, String l2Category, String l3Category, String archetypeProduct) {
		return ImmutableMatchDiagnostics.builder()
				.l1Category(l1Category)
				.l2Category(l2Category)
				.l3Category(l3Category)
				.archetypeProduct(archetypeProduct)
				.build();
	}

	private static ArchetypeCategory category(String id, String name) {
		return ImmutableArchetypeCategory.builder().id(new ArchetypeCategoryIdImpl(id)).name(name).build();
	}

	private static ArchetypeProduct product(ArchetypeProductId id, String name) {
		return ImmutableArchetypeProduct.builder().id(id).name(name).build();
	}
}
