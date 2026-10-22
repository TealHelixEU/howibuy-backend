package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import eu.tealhelix.howibuy.v1.model.ImmutableMatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.MatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.ProductBaseData;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Descends the SAFAD taxonomy (L1 category → L2 subcategory → L3 subcategory → archetype product) for a single product,
 * asking the AI to pick at each level and resolving the pick against the candidates loaded from the DB. It produces an
 * {@link ArchetypeMatchOutcome}: {@code Matched} when the descent reaches an archetype product,
 * {@code NoMatch} when the AI reports no match, or {@code Malformed} when the AI violates the prompt contract.
 * Unexpected failures (a DB error, an open circuit breaker) are left on the {@code Uni} failure channel for the caller
 * to handle.
 */
@ApplicationScoped
public class ArchetypeMatching {
	private static final Logger LOG = LoggerFactory.getLogger(ArchetypeMatching.class);

	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final ProductAssessmentAiFacade productAssessmentAiFacade;
	private final ArchetypeCategoryDao archetypeCategoryDao;
	private final ArchetypeProductDao archetypeProductDao;
	private final FoodTermGlossary foodTermGlossary;
	private final ProductClassificationGuidance productClassificationGuidance;

	@Inject
	public ArchetypeMatching(
			ReactivePersistenceContextFactory persistenceContextFactory,
			ProductAssessmentAiFacade productAssessmentAiFacade,
			ArchetypeCategoryDao archetypeCategoryDao,
			ArchetypeProductDao archetypeProductDao,
			FoodTermGlossary foodTermGlossary,
			ProductClassificationGuidance productClassificationGuidance
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.productAssessmentAiFacade = productAssessmentAiFacade;
		this.archetypeCategoryDao = archetypeCategoryDao;
		this.archetypeProductDao = archetypeProductDao;
		this.foodTermGlossary = foodTermGlossary;
		this.productClassificationGuidance = productClassificationGuidance;
	}

	/**
	 * Matches one product to an Archetype Product, turning every {@link DescentStopped} raised by the descent into
	 * the outcome it carries.
	 */
	public Uni<ArchetypeMatchOutcome> match(ProductBaseData productData) {
		return forc(
				recognizedTermsFor(productData),
				recognizedTerms -> descendCategories(productData, recognizedTerms)
		)
				.onFailure(DescentStopped.class)
				.recoverWithItem(failure -> ((DescentStopped) failure).outcome);
	}

	/**
	 * Looks up the glossary terms occurring in the product name and logs them, so the curated knowledge fed to the
	 * classifier can be audited from the logs and the dataset corrected. The same enrichment is shown to the AI at every
	 * level of the descent.
	 */
	private Uni<List<FoodTerm>> recognizedTermsFor(ProductBaseData productData) {
		String language = productData.getLanguage().getLanguage();
		return foodTermGlossary.match(language, productData.getName())
				.invoke(recognizedTerms -> logEnrichment(language, productData.getName(), recognizedTerms));
	}

	private Uni<ArchetypeMatchOutcome> descendCategories(ProductBaseData productData, List<FoodTerm> recognizedTerms) {
		return forc(
				retrieveL1Categories(),
				l1categories -> resolveCategory(0, l1categories, recognizedTerms,
						() -> productAssessmentAiFacade.extractL1Category(productData, categoryNames(l1categories), recognizedTerms),
						diagnostics(null, null, null, null)
				),
				(_, l1category) -> retrieveSubcategoriesOf(l1category),
				(_, l1category, l2categories) -> resolveCategory(1, l2categories, recognizedTerms,
						() -> productAssessmentAiFacade.extractSubcategory(productData, categoryNames(l2categories), recognizedTerms),
						diagnostics(l1category.getName(), null, null, null)
				),
				(_, _, _, l2category) -> retrieveSubcategoriesOf(l2category),
				(_, l1category, _, l2category, l3categories) -> resolveCategory(2, l3categories, recognizedTerms,
						() -> productAssessmentAiFacade.extractSubcategory(productData, categoryNames(l3categories), recognizedTerms),
						diagnostics(l1category.getName(), l2category.getName(), null, null)
				),
				(_, _, _, l2category, _, l3category) -> retrieveProductsInCategory(l3category),
				(_, l1category, _, l2category, _, l3category, products) ->
						extractProduct(productData, l1category.getName(), l2category.getName(), l3category.getName(), products, recognizedTerms)
		);
	}

	/**
	 * Chooses the category for one level of the descent. When the recognized glossary terms unambiguously point to one
	 * of the candidates (see {@link #glossaryHintedCategory}), that candidate is taken directly and the AI is not
	 * consulted for this level; otherwise the choice is delegated to the AI via {@code aiCall}.
	 */
	private Uni<ArchetypeCategory> resolveCategory(
			int level, List<ArchetypeCategory> candidates, List<FoodTerm> recognizedTerms,
			Supplier<Uni<AiSelection>> aiCall, MatchDiagnostics diagnosticsSoFar) {
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

	private Uni<List<ArchetypeCategory>> retrieveL1Categories() {
		return persistenceContextFactory.withoutTransaction(archetypeCategoryDao::retrieveL1Categories);
	}

	/**
	 * Resolves the AI's selection against the candidates it was shown: a {@link AiSelection.Match} yields the chosen
	 * candidate, while a {@link AiSelection.None} and a {@link AiSelection.Malformed} reply stop the descent with their
	 * outcome. A malformed reply is logged so its rate can be monitored.
	 */
	private static <C> Uni<C> pick(Uni<AiSelection> selection, List<C> candidates, String kind, MatchDiagnostics diagnosticsSoFar) {
		return selection.flatMap(chosen -> switch (chosen) {
			case AiSelection.Match match -> Uni.createFrom().item(candidates.get(match.index()));
			case AiSelection.None _ -> stop(new ArchetypeMatchOutcome.NoMatch(diagnosticsSoFar));
			case AiSelection.Malformed malformed -> {
				LOG.warn("AI selection was not a valid candidate number, kind: {}, raw reply: '{}'", kind, malformed.rawReply());
				yield stop(new ArchetypeMatchOutcome.Malformed(diagnosticsSoFar, kind, malformed.rawReply()));
			}
		});
	}

	private Uni<ArchetypeMatchOutcome> extractProduct(ProductBaseData productData, String l1name, String l2name, String l3name, List<ArchetypeProduct> products, List<FoodTerm> recognizedTerms) {
		String categoryGuidance = productClassificationGuidance.forCategoryPath(
				productData.getLanguage().getLanguage(), List.of(l1name, l2name, l3name));
		return pick(productAssessmentAiFacade.extractArchetypeProduct(productData, productNames(products), recognizedTerms, categoryGuidance), products, "product", diagnostics(l1name, l2name, l3name, null))
				.map(product -> new ArchetypeMatchOutcome.Matched(
						product.getId(), diagnostics(l1name, l2name, l3name, product.getName())));
	}

	private Uni<List<ArchetypeCategory>> retrieveSubcategoriesOf(ArchetypeCategory parent) {
		return persistenceContextFactory.withoutTransaction(em -> archetypeCategoryDao.retrieveSubcategories(em, parent));
	}

	private Uni<List<ArchetypeProduct>> retrieveProductsInCategory(ArchetypeCategory category) {
		return persistenceContextFactory.withoutTransaction(em -> archetypeProductDao.retrieveProductsInCategory(em, category));
	}

	private static List<String> categoryNames(List<ArchetypeCategory> categories) {
		return categories.stream().map(ArchetypeCategory::getName).toList();
	}

	private static List<String> productNames(List<ArchetypeProduct> products) {
		return products.stream().map(ArchetypeProduct::getName).toList();
	}

	private static MatchDiagnostics diagnostics(String l1category, String l2category, String l3category, String archetypeProduct) {
		return ImmutableMatchDiagnostics.builder()
				.l1Category(l1category)
				.l2Category(l2category)
				.l3Category(l3category)
				.archetypeProduct(archetypeProduct)
				.build();
	}

	private static <O> Uni<O> stop(ArchetypeMatchOutcome outcome) {
		return Uni.createFrom().failure(new DescentStopped(outcome));
	}

	/**
	 * Signals, on the {@code Uni} failure channel, that the descent ended before reaching an archetype product, carrying
	 * the outcome it ended with. It is a control-flow signal that lets the flat {@code forc} descent short-circuit;
	 * {@link #match} turns it into its outcome — it is not a real error, and never escapes this class.
	 * <p>
	 * {@link #fillInStackTrace()} is overridden to a no-op because the stack trace is never read: it makes the
	 * control-flow intent explicit to readers and avoids the cost of capturing a stack on every occurrence.
	 */
	private static final class DescentStopped extends RuntimeException {
		private final ArchetypeMatchOutcome outcome;

		private DescentStopped(ArchetypeMatchOutcome outcome) {
			this.outcome = outcome;
		}

		@Override
		public Throwable fillInStackTrace() {
			return this;
		}
	}
}
