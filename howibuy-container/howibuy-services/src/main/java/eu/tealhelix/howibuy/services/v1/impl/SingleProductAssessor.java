package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.SUCCESS;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import io.smallrye.mutiny.Uni;

/**
 * Assesses a single product whose archetype {@link ReferenceProductIdentifier} looked up: when it is identified,
 * ranks what to buy instead. It produces a {@link ProductAssessmentOutcome}: {@code SUCCESS} when the archetype is
 * identified, {@code FAILURE_TO_IDENTIFY} with the reason when it is not. Unexpected failures are left on the
 * {@code Uni} failure channel for the caller to surface (as a server error) or isolate (per product, in a batch).
 */
@ApplicationScoped
public class SingleProductAssessor {
	private final ArchetypeCorpus archetypeCorpus;

	@Inject
	public SingleProductAssessor(ArchetypeCorpus archetypeCorpus) {
		this.archetypeCorpus = archetypeCorpus;
	}

	public Uni<ProductAssessmentOutcome> assessOne(ProductKey productKey, ProductIdentification identification, WeightProfile personalProfile) {
		return switch (identification) {
			case ProductIdentification.Identified identified -> successfulAssessment(productKey, identified, personalProfile);
			case ProductIdentification.NotIdentified notIdentified -> Uni.createFrom().item(ImmutableProductAssessmentOutcome.builder()
					.productKey(productKey)
					.type(FAILURE_TO_IDENTIFY)
					.failureReason(notIdentified.reason())
					.diagnostics(notIdentified.diagnostics())
					.build());
		};
	}

	/**
	 * The assessed product's archetype identified, what to buy instead follows from the scored corpus: three rankings
	 * of the archetypes that may substitute for its category, each reporting its own winner. A winner that is the
	 * archetype itself says the user already has the best available choice.
	 */
	private Uni<ProductAssessmentOutcome> successfulAssessment(
			ProductKey productKey,
			ProductIdentification.Identified identified,
			WeightProfile personalProfile
	) {
		return archetypeCorpus.scoredArchetypes()
				.map(archetypes -> archetypes.recommendationsFor(identified.archetypeProductId(), personalProfile))
				.map(best -> ImmutableProductAssessmentOutcome.builder()
						.productKey(productKey)
						.type(SUCCESS)
						.bestPersonalAlternative(best.personal())
						.bestScientificAlternative(best.scientific())
						.bestCombinedAlternative(best.combined())
						.diagnostics(identified.diagnostics())
						.build());
	}

	static ProductAssessmentOutcome outcome(ProductKey productKey, ProductAssessmentOutcomeType type) {
		return ImmutableProductAssessmentOutcome.builder()
				.productKey(productKey)
				.type(type)
				.build();
	}
}
