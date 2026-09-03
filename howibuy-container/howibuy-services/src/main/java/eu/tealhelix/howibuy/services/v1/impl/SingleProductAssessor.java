package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_OTHER;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.SUCCESS;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.howibuy.dao.ArchetypeCategoryDao;
import eu.tealhelix.howibuy.dao.ArchetypeProductDao;
import eu.tealhelix.howibuy.services.model.ArchetypeCategory;
import eu.tealhelix.howibuy.services.model.ArchetypeProduct;
import eu.tealhelix.howibuy.services.model.FoodTerm;
import eu.tealhelix.howibuy.services.v1.ai.AiSelection;
import eu.tealhelix.howibuy.services.v1.ai.ProductAssessmentAiFacade;
import eu.tealhelix.howibuy.services.v1.enrichment.FoodTermGlossary;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcomeDiagnostics;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcomeDiagnostics;
import eu.tealhelix.howibuy.v1.model.ProductData;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
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

	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final ProductAssessmentAiFacade productAssessmentAiFacade;
	private final ArchetypeCategoryDao archetypeCategoryDao;
	private final ArchetypeProductDao archetypeProductDao;
	private final FoodTermGlossary foodTermGlossary;
	private final ProductClassificationGuidance productClassificationGuidance;
	private final ArchetypeCorpus archetypeCorpus;

	@Inject
	public SingleProductAssessor(
			ReactivePersistenceContextFactory persistenceContextFactory,
			ProductAssessmentAiFacade productAssessmentAiFacade,
			ArchetypeCategoryDao archetypeCategoryDao,
			ArchetypeProductDao archetypeProductDao,
			FoodTermGlossary foodTermGlossary,
			ProductClassificationGuidance productClassificationGuidance,
			ArchetypeCorpus archetypeCorpus
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.productAssessmentAiFacade = productAssessmentAiFacade;
		this.archetypeCategoryDao = archetypeCategoryDao;
		this.archetypeProductDao = archetypeProductDao;
		this.foodTermGlossary = foodTermGlossary;
		this.productClassificationGuidance = productClassificationGuidance;
		this.archetypeCorpus = archetypeCorpus;
	}

	/**
	 * Assesses one product, turning every {@link ProductNotAssessedException} raised by the descent into its
	 * corresponding non-{@code SUCCESS} outcome. Unexpected failures (a DB error, an open circuit breaker) are left on
	 * the failure channel for the caller to surface (as a server error) or isolate (per product, in a batch).
	 */
	public Uni<ProductAssessmentOutcome> assessOne(ProductData productData, WeightProfile personalProfile) {
		return assessOneWithoutHandlingFailures(productData, personalProfile)
				.onFailure(ProductNotAssessedException.class)
				.recoverWithItem(failure -> outcomeFor(productData, failure));
	}

	private Uni<ProductAssessmentOutcome> assessOneWithoutHandlingFailures(ProductData productData, WeightProfile personalProfile) {
		return forc(
				recognizedTermsFor(productData),
				recognizedTerms -> descendCategories(productData, recognizedTerms, personalProfile)
		);
	}

	/**
	 * Looks up the glossary terms occurring in the product name and logs them, so the curated knowledge fed to the
	 * classifier can be audited from the logs and the dataset corrected. The same enrichment is shown to the AI at every
	 * level of the descent.
	 */
	private Uni<List<FoodTerm>> recognizedTermsFor(ProductData productData) {
		String language = productData.getLanguage().getLanguage();
		return foodTermGlossary.match(language, productData.getName())
				.invoke(recognizedTerms -> logEnrichment(language, productData.getName(), recognizedTerms));
	}

	private Uni<ProductAssessmentOutcome> descendCategories(
			ProductData productData, List<FoodTerm> recognizedTerms, WeightProfile personalProfile) {
		return forc(
				retrieveL1Categories(),
				l1categories -> resolveCategory(0, l1categories, recognizedTerms,
						() -> productAssessmentAiFacade.extractL1Category(productData, categoryNames(l1categories), recognizedTerms),
						diagnostics(null, null, null, null)
				),
				(_, l1category) -> retrieveSubcategoriesOf(l1category.getId()),
				(_, l1category, l2categories) -> resolveCategory(1, l2categories, recognizedTerms,
						() -> productAssessmentAiFacade.extractSubcategory(productData, categoryNames(l2categories), recognizedTerms),
						diagnostics(l1category.getName(), null, null, null)
				),
				(_, _, _, l2category) -> retrieveSubcategoriesOf(l2category.getId()),
				(_, l1category, _, l2category, l3categories) -> resolveCategory(2, l3categories, recognizedTerms,
						() -> productAssessmentAiFacade.extractSubcategory(productData, categoryNames(l3categories), recognizedTerms),
						diagnostics(l1category.getName(), l2category.getName(), null, null)
				),
				(_, _, _, l2category, _, l3category) -> retrieveProductsInCategory(l3category.getId()),
				(_, l1category, _, l2category, _, l3category, products) ->
						extractProduct(productData, l1category.getName(), l2category.getName(), l3category.getName(), products, recognizedTerms, personalProfile)
		);
	}

	/**
	 * Chooses the category for one level of the descent. When the recognized glossary terms unambiguously point to one
	 * of the candidates (see {@link #glossaryHintedCategory}), that candidate is taken directly and the AI is not
	 * consulted for this level; otherwise the choice is delegated to the AI via {@code aiCall}.
	 */
	private Uni<ArchetypeCategory> resolveCategory(
			int level, List<ArchetypeCategory> candidates, List<FoodTerm> recognizedTerms,
			Supplier<Uni<AiSelection>> aiCall, ProductAssessmentOutcomeDiagnostics diagnosticsSoFar) {
		return glossaryHintedCategory(level, candidates, recognizedTerms)
				.map(category -> Uni.createFrom().item(category))
				.orElseGet(() -> pick(aiCall.get(), candidates, "category", diagnosticsSoFar));
	}

	/**
	 * The candidate uniquely named by the recognized terms' category hints at this level, if any. Each hint is a
	 * category path from L1 downward; the node at position {@code level} is the category it prescribes here. The choice
	 * is left to the AI (empty result) unless exactly one candidate is named — so a level with no hint, or with hints
	 * naming more than one candidate, still goes to the AI.
	 */
	private static Optional<ArchetypeCategory> glossaryHintedCategory(
			int level, List<ArchetypeCategory> candidates, List<FoodTerm> recognizedTerms) {
		Set<String> hintedNames = recognizedTerms.stream()
				.map(FoodTerm::getCategoryHintPath)
				.filter(path -> path.size() > level)
				.map(path -> path.get(level))
				.collect(Collectors.toSet());
		List<ArchetypeCategory> named = candidates.stream()
				.filter(candidate -> hintedNames.contains(candidate.getName()))
				.toList();
		return named.size() == 1 ? Optional.of(named.get(0)) : Optional.empty();
	}

	private static void logEnrichment(String language, String name, List<FoodTerm> recognizedTerms) {
		if (recognizedTerms.isEmpty()) {
			LOG.debug("Product enrichment found no glossary terms, language: {}, name: '{}'", language, name);
		} else {
			LOG.info("Product enrichment glossary matches, language: {}, name: '{}', matches: {}", language, name, describeMatches(recognizedTerms));
		}
	}

	private static String describeMatches(List<FoodTerm> recognizedTerms) {
		return recognizedTerms.stream()
				.map(term -> term.getTerm() + " → " + term.getCanonicalEn() + term.getCategoryHint().map(hint -> " (" + hint + ")").orElse(""))
				.collect(Collectors.joining("; ", "[", "]"));
	}

	private ProductAssessmentOutcome outcomeFor(ProductData productData, ProductNotAssessedException failure) {
		return switch (failure) {
			case FailureToIdentifyException e -> outcome(productData, FAILURE_TO_IDENTIFY, e.getDiagnostics());
			case AiPickNotAmongCandidatesException e -> {
				LOG.warn("AI selection for {} was not a valid candidate number, reporting {}, raw reply: '{}'",
						e.getKind(), FAILURE_OTHER, e.getRawReply());
				yield outcome(productData, FAILURE_OTHER, e.getDiagnostics());
			}
		};
	}

	private Uni<List<ArchetypeCategory>> retrieveL1Categories() {
		return persistenceContextFactory.withoutTransaction(archetypeCategoryDao::retrieveL1Categories);
	}

	/**
	 * Resolves the AI's selection against the candidates it was shown: a {@link AiSelection.Match} yields the chosen
	 * candidate, a {@link AiSelection.None} short-circuits to {@link FailureToIdentifyException}, and a
	 * {@link AiSelection.Malformed} reply to {@link AiPickNotAmongCandidatesException}.
	 */
	private static <C> Uni<C> pick(Uni<AiSelection> selection, List<C> candidates, String kind, ProductAssessmentOutcomeDiagnostics diagnosticsSoFar) {
		return selection.flatMap(chosen -> switch (chosen) {
			case AiSelection.Match match -> Uni.createFrom().item(candidates.get(match.index()));
			case AiSelection.None _ -> failureToIdentify(diagnosticsSoFar);
			case AiSelection.Malformed malformed -> Uni.createFrom()
					.failure(new AiPickNotAmongCandidatesException(diagnosticsSoFar, kind, malformed.rawReply()));
		});
	}

	private Uni<ProductAssessmentOutcome> extractProduct(ProductData productData, String l1name, String l2name, String l3name, List<ArchetypeProduct> products, List<FoodTerm> recognizedTerms, WeightProfile personalProfile) {
		ProductAssessmentOutcomeDiagnostics diagnostics = diagnostics(l1name, l2name, l3name, null);
		String categoryGuidance = productClassificationGuidance.forCategoryPath(
				productData.getLanguage().getLanguage(), List.of(l1name, l2name, l3name));
		return pick(productAssessmentAiFacade.extractArchetypeProduct(productData, productNames(products), recognizedTerms, categoryGuidance), products, "product", diagnostics)
				.flatMap(product -> successfulAssessment(
						productData, diagnostics(l1name, l2name, l3name, product.getName()), product.getId(), personalProfile));
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

	private static ProductAssessmentOutcomeDiagnostics diagnostics(String l1category, String l2category, String l3category, String product) {
		return ImmutableProductAssessmentOutcomeDiagnostics.builder()
				.l1Category(l1category)
				.l2Category(l2category)
				.l3Category(l3category)
				.product(product)
				.build();
	}

	/**
	 * The assessed product's archetype decided, what to buy instead follows from the scored corpus: three rankings of
	 * the archetypes that may substitute for its category, each reporting its own winner. A winner that is the
	 * archetype itself says the user already has the best available choice.
	 */
	private Uni<ProductAssessmentOutcome> successfulAssessment(
			ProductData productData,
			ProductAssessmentOutcomeDiagnostics diagnostics,
			UUID archetypeProductId,
			WeightProfile personalProfile
	) {
		return archetypeCorpus.scoredArchetypes()
				.map(archetypes -> archetypes.recommendationsFor(archetypeProductId, personalProfile))
				.map(best -> ImmutableProductAssessmentOutcome.builder()
						.productKey(productData.getProductKey())
						.type(SUCCESS)
						.bestPersonalAlternative(best.personal())
						.bestScientificAlternative(best.scientific())
						.bestCombinedAlternative(best.combined())
						.diagnostics(diagnostics)
						.build());
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
}
