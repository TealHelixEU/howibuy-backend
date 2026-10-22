package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_OTHER;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.SUCCESS;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.howibuy.v1.model.MatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductData;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import io.smallrye.mutiny.Uni;

/**
 * Assesses a single product: matches it to an archetype product through the {@link ArchetypeMatching} and, when
 * it matches one, ranks what to buy instead. It produces a {@link ProductAssessmentOutcome}: {@code SUCCESS} when
 * it matches one, {@code FAILURE_TO_IDENTIFY} when the AI reports no match, or {@code FAILURE_OTHER}
 * when the AI violates the prompt contract. Unexpected failures (a DB error, an open circuit breaker) are left on the
 * {@code Uni} failure channel for the caller to surface (as a server error) or isolate (per product, in a batch).
 */
@ApplicationScoped
public class SingleProductAssessor {
	private final ArchetypeMatching archetypeMatching;
	private final ArchetypeCorpus archetypeCorpus;

	@Inject
	public SingleProductAssessor(ArchetypeMatching archetypeMatching, ArchetypeCorpus archetypeCorpus) {
		this.archetypeMatching = archetypeMatching;
		this.archetypeCorpus = archetypeCorpus;
	}

	public Uni<ProductAssessmentOutcome> assessOne(ProductData productData, WeightProfile personalProfile) {
		return archetypeMatching.match(productData)
				.flatMap(matchOutcome -> switch (matchOutcome) {
					case ArchetypeMatchOutcome.Matched matched -> successfulAssessment(
							productData, matched.diagnostics(), matched.archetypeProductId(), personalProfile);
					case ArchetypeMatchOutcome.NoMatch noMatch ->
							Uni.createFrom().item(outcome(productData, FAILURE_TO_IDENTIFY, noMatch.diagnostics()));
					case ArchetypeMatchOutcome.Malformed malformed ->
							Uni.createFrom().item(outcome(productData, FAILURE_OTHER, malformed.diagnostics()));
				});
	}

	/**
	 * The assessed product's archetype decided, what to buy instead follows from the scored corpus: three rankings of
	 * the archetypes that may substitute for its category, each reporting its own winner. A winner that is the
	 * archetype itself says the user already has the best available choice.
	 */
	private Uni<ProductAssessmentOutcome> successfulAssessment(
			ProductData productData,
			MatchDiagnostics diagnostics,
			ArchetypeProductId archetypeProductId,
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

	static ProductAssessmentOutcome outcome(
			ProductData productData, ProductAssessmentOutcomeType type,
			MatchDiagnostics diagnostics) {
		return ImmutableProductAssessmentOutcome.builder()
				.productKey(productData.getProductKey())
				.type(type)
				.diagnostics(diagnostics)
				.build();
	}
}
