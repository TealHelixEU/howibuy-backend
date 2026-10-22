package eu.tealhelix.howibuy.services.v1.classification;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;

/**
 * What a {@link ClassificationResolution} resolved a product's Classification to.
 *
 * @param archetypeProductId The archetype the product is; {@code null} when no archetype matches it
 * @param needsReview        Whether the attempts that decided it disagree, so a person should look at it
 */
public record Resolution(@Nullable ArchetypeProductId archetypeProductId, boolean needsReview) {
}
