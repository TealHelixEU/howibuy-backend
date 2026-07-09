package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.NO_SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_OTHER;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.SUCCESS;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.howibuy.dao.ArchetypeCategoryDao;
import eu.tealhelix.howibuy.dao.ArchetypeProductDao;
import eu.tealhelix.howibuy.services.model.ArchetypeCategory;
import eu.tealhelix.howibuy.services.model.ArchetypeProduct;
import eu.tealhelix.howibuy.services.v1.ai.ProductAssessmentAiFacade;
import eu.tealhelix.howibuy.v1.model.ImmutableAlternativeForProduct;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcomeDiagnostics;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcomeDiagnostics;
import eu.tealhelix.howibuy.v1.model.ProductData;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Descends the SAFAD taxonomy (L1 category → L2 subcategory → L3 subcategory → archetype product) for a single product,
 * asking the AI to pick at each level and resolving the pick against the candidates loaded from the DB. It produces a
 * {@link ProductAssessmentOutcome}: {@code SUCCESS} when the descent reaches an archetype product, or a non-{@code
 * SUCCESS} outcome when the AI reports no match ({@code FAILURE_TO_IDENTIFY}) or violates the prompt contract
 * ({@code FAILURE_OTHER}). Unexpected failures (a DB error, an open circuit breaker) are left on the {@code Uni} failure
 * channel for the caller to handle.
 */
@ApplicationScoped
public class SingleProductAssessor {
	private static final Logger LOG = LoggerFactory.getLogger(SingleProductAssessor.class);
	private static final String NO_MATCH = "NONE";

	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final ProductAssessmentAiFacade productAssessmentAiFacade;
	private final ArchetypeCategoryDao archetypeCategoryDao;
	private final ArchetypeProductDao archetypeProductDao;

	@Inject
	public SingleProductAssessor(
			ReactivePersistenceContextFactory persistenceContextFactory,
			ProductAssessmentAiFacade productAssessmentAiFacade,
			ArchetypeCategoryDao archetypeCategoryDao,
			ArchetypeProductDao archetypeProductDao
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.productAssessmentAiFacade = productAssessmentAiFacade;
		this.archetypeCategoryDao = archetypeCategoryDao;
		this.archetypeProductDao = archetypeProductDao;
	}

	/**
	 * Assesses one product, turning every {@link ProductNotAssessedException} raised by the descent into its
	 * corresponding non-{@code SUCCESS} outcome. Unexpected failures (a DB error, an open circuit breaker) are left on
	 * the failure channel for the caller to surface (as a server error) or isolate (per product, in a batch).
	 */
	public Uni<ProductAssessmentOutcome> assessOne(ProductData productData) {
		return assessOneWithoutHandlingFailures(productData)
				.onFailure(ProductNotAssessedException.class)
				.recoverWithItem(failure -> outcomeFor(productData, failure));
	}

	private Uni<ProductAssessmentOutcome> assessOneWithoutHandlingFailures(ProductData productData) {
		return forc(
				retrieveL1Categories(),
				l1categories -> pickOrFail(productAssessmentAiFacade.extractL1Category(productData, categoryNames(l1categories)),
						diagnostics(null, null, null, null)),
				(l1categories, l1name) -> retrieveSubcategoriesOf(idOfPicked(l1categories, l1name, diagnostics(null, null, null, null))),
				(_, l1name, l2categories) -> pickOrFail(productAssessmentAiFacade.extractSubcategory(productData, categoryNames(l2categories)),
						diagnostics(l1name, null, null, null)),
				(_, l1name, l2categories, l2name) -> retrieveSubcategoriesOf(idOfPicked(l2categories, l2name, diagnostics(l1name, null, null, null))),
				(_, l1name, _, l2name, l3categories) -> pickOrFail(productAssessmentAiFacade.extractSubcategory(productData, categoryNames(l3categories)),
						diagnostics(l1name, l2name, null, null)),
				(_, l1name, _, l2name, l3categories, l3name) -> retrieveProductsInCategory(idOfPicked(l3categories, l3name, diagnostics(l1name, l2name, null, null))),
				(_, l1name, _, l2name, _, l3name, products) -> extractProduct(productData, l1name, l2name, l3name, products)
		);
	}

	private ProductAssessmentOutcome outcomeFor(ProductData productData, ProductNotAssessedException failure) {
		return switch (failure) {
			case FailureToIdentifyException e -> outcome(productData, FAILURE_TO_IDENTIFY, e.getDiagnostics());
			case AiPickNotAmongCandidatesException e -> {
				LOG.warn("AI picked {} not among the candidates, reporting {}: '{}'",
						e.getKind(), FAILURE_OTHER, e.getPickedName());
				yield outcome(productData, FAILURE_OTHER, e.getDiagnostics());
			}
		};
	}

	private Uni<List<ArchetypeCategory>> retrieveL1Categories() {
		return persistenceContextFactory.withoutTransaction(archetypeCategoryDao::retrieveL1Categories);
	}

	private static Uni<String> pickOrFail(Uni<String> pick, ProductAssessmentOutcomeDiagnostics diagnosticsIfNoMatch) {
		return pick.flatMap(name -> isNoMatch(name)
				? failureToIdentify(diagnosticsIfNoMatch)
				: Uni.createFrom().item(name));
	}

	private Uni<ProductAssessmentOutcome> extractProduct(ProductData productData, String l1name, String l2name, String l3name, List<ArchetypeProduct> products) {
		ProductAssessmentOutcomeDiagnostics diagnostics = diagnostics(l1name, l2name, l3name, null);
		return pickOrFail(productAssessmentAiFacade.extractArchetypeProduct(productData, productNames(products)), diagnostics)
				.map(productName -> requirePicked(products, productName, ArchetypeProduct::getName, "product", diagnostics))
				.flatMap(product -> successfulAssessment(productData, diagnostics(l1name, l2name, l3name, product.getName())));
	}

	private Uni<List<ArchetypeCategory>> retrieveSubcategoriesOf(UUID parentId) {
		return persistenceContextFactory.withoutTransaction(em -> archetypeCategoryDao.retrieveSubcategories(em, parentId));
	}

	private Uni<List<ArchetypeProduct>> retrieveProductsInCategory(UUID categoryId) {
		return persistenceContextFactory.withoutTransaction(em -> archetypeProductDao.retrieveProductsInCategory(em, categoryId));
	}

	private static List<String> categoryNames(List<ArchetypeCategory> categories) {
		return categories.stream().map(ArchetypeCategory::getName).toList();
	}

	private static List<String> productNames(List<ArchetypeProduct> products) {
		return products.stream().map(ArchetypeProduct::getName).toList();
	}

	private static boolean isNoMatch(String pickedName) {
		return NO_MATCH.equals(pickedName);
	}

	private static UUID idOfPicked(List<ArchetypeCategory> categories, String name, ProductAssessmentOutcomeDiagnostics diagnosticsSoFar) {
		return requirePicked(categories, name, ArchetypeCategory::getName, "category", diagnosticsSoFar).getId();
	}

	private static <C> C requirePicked(
			List<C> candidates,
			String pickedName,
			Function<C, String> nameOf,
			String kind,
			ProductAssessmentOutcomeDiagnostics diagnosticsSoFar
	) {
		return candidates.stream()
				.filter(candidate -> nameOf.apply(candidate).equals(pickedName))
				.findFirst()
				.orElseThrow(() -> {
					if (LOG.isDebugEnabled()) {
						String allCategories = candidates.stream().map(nameOf).collect(Collectors.joining("\", \"", "\"", "\""));
						LOG.debug("AI picked not among the candidates, pick: \"{}\", all candidates of kind {}: {}", pickedName, kind, allCategories);
					}
					LOG.debug("AI picked {} not among the candidates, reporting {}: '{}'", kind, FAILURE_OTHER, pickedName);
					return new AiPickNotAmongCandidatesException(diagnosticsSoFar, kind, pickedName);
				});
	}

	private static ProductAssessmentOutcomeDiagnostics diagnostics(String l1category, String l2category, String l3category, String product) {
		return ImmutableProductAssessmentOutcomeDiagnostics.builder()
				.l1Category(l1category)
				.l2Category(l2category)
				.l3Category(l3category)
				.product(product)
				.build();
	}

	private Uni<ProductAssessmentOutcome> successfulAssessment(ProductData productData, ProductAssessmentOutcomeDiagnostics diagnostics) {
		var output = (ImmutableProductAssessmentOutcome) makeDummyProductAssessmentOutcome(productData);
		return Uni.createFrom().item(output.withDiagnostics(diagnostics));
	}

	private static <O> Uni<O> failureToIdentify(ProductAssessmentOutcomeDiagnostics diagnostics) {
		return Uni.createFrom().failure(new FailureToIdentifyException(diagnostics));
	}

	static ProductAssessmentOutcome outcome(
			ProductData productData, ProductAssessmentOutcomeType type,
			ProductAssessmentOutcomeDiagnostics diagnostics) {
		return ImmutableProductAssessmentOutcome.builder()
				.productKey(productData.getProductKey())
				.type(type)
				.diagnostics(diagnostics)
				.build();
	}

	private ProductAssessmentOutcome makeDummyProductAssessmentOutcome(ProductData productData) {
		var dummyAlternative = ImmutableAlternativeForProduct.builder()
				.type(SUGGESTION)
				.name("The best personal alternative")
				.build();
		return ImmutableProductAssessmentOutcome.builder()
				.productKey(productData.getProductKey())
				.type(SUCCESS)
				.bestPersonalAlternative(dummyAlternative)
				.bestScientificAlternative(ImmutableAlternativeForProduct.builder().type(NO_SUGGESTION).build())
				.bestCombinedAlternative(dummyAlternative)
				.build();
	}
}
