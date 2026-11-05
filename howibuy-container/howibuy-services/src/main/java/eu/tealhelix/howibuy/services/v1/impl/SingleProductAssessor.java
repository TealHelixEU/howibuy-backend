package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.SUCCESS;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import io.smallrye.mutiny.Uni;

/**
 * Assesses a single product whose archetype {@link ReferenceProductIdentifier} looked up: when it is identified,
 * recommends what to buy instead. It produces a {@link ProductAssessmentOutcome}: {@code SUCCESS} when the archetype
 * is identified, {@code FAILURE_TO_IDENTIFY} with the reason when it is not. Unexpected failures are left on the
 * {@code Uni} failure channel for the caller to surface (as a server error) or isolate (per product, in a batch).
 */
@ApplicationScoped
public class SingleProductAssessor {
	private final ArchetypeCorpus archetypeCorpus;

	@Inject
	public SingleProductAssessor(ArchetypeCorpus archetypeCorpus) {
		this.archetypeCorpus = archetypeCorpus;
	}

	/**
	 * @param userId          The user the product is assessed for, whom the draws of the Alternatives are seeded from
	 * @param productKey      The key of the scanned product
	 * @param identification  How the archetype of the product was looked up
	 * @param personalProfile The user's weighting profile
	 * @param stocked         The archetypes the user's Catalogue stocks, the only ones an Alternative may be
	 * @return The outcome of the assessment
	 */
	public Uni<ProductAssessmentOutcome> assessOne(
			UserId userId, ProductKey productKey, ProductIdentification identification, WeightProfile personalProfile, Set<ArchetypeProductId> stocked) {
		return switch (identification) {
			case ProductIdentification.Identified identified -> successfulAssessment(userId, productKey, identified, personalProfile, stocked);
			case ProductIdentification.NotIdentified notIdentified -> Uni.createFrom().item(ImmutableProductAssessmentOutcome.builder()
					.productKey(productKey)
					.type(FAILURE_TO_IDENTIFY)
					.failureReason(notIdentified.reason())
					.diagnostics(notIdentified.diagnostics())
					.build());
		};
	}

	/**
	 * The assessed product's archetype identified, the Recommendation follows from the scored corpus, its draws seeded
	 * from the user and the archetype.
	 */
	private Uni<ProductAssessmentOutcome> successfulAssessment(
			UserId userId,
			ProductKey productKey,
			ProductIdentification.Identified identified,
			WeightProfile personalProfile,
			Set<ArchetypeProductId> stocked
	) {
		var referenceProductId = identified.archetypeProductId();
		return archetypeCorpus.scoredArchetypes()
				.map(archetypes -> archetypes.recommendationFor(
						referenceProductId, personalProfile, stocked, new Random(seedOf(userId, referenceProductId))))
				.map(recommendation -> ImmutableProductAssessmentOutcome.builder()
						.productKey(productKey)
						.type(SUCCESS)
						.recommendation(recommendation.type())
						.referenceOverallScore(recommendation.referenceOverallScore())
						.alternatives(recommendation.alternatives())
						.diagnostics(identified.diagnostics())
						.build());
	}

	/**
	 * The seed of the draws for a user and a Reference Product, so that the same user scanning the same product is
	 * offered the same Alternatives (ADR 0007).
	 * <p>
	 * It is fixed by specification, so that the Alternatives survive restarts and redeploys: the name-based UUID of
	 * RFC 4122 (version 3, MD5) of {@code "<user id>/<archetype id>"}, its two halves XORed. The hash also keeps the
	 * seeds of ids that differ in a character or two far apart: the first numbers {@link Random} draws from close seeds
	 * are close too.
	 */
	static long seedOf(UserId userId, ArchetypeProductId referenceProductId) {
		var name = UUID.nameUUIDFromBytes((userId.asString() + "/" + referenceProductId.asString()).getBytes(StandardCharsets.UTF_8));
		return name.getMostSignificantBits() ^ name.getLeastSignificantBits();
	}

	static ProductAssessmentOutcome outcome(ProductKey productKey, ProductAssessmentOutcomeType type) {
		return ImmutableProductAssessmentOutcome.builder()
				.productKey(productKey)
				.type(type)
				.build();
	}
}
