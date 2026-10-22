package eu.tealhelix.howibuy.v1.model;

import java.time.LocalDateTime;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import org.immutables.value.Value;

/**
 * The one match of a Retailer Product to an Archetype Product, resolved from its Classification Attempts.
 */
@Value.Immutable
public interface Classification {
	/**
	 * The archetype the product is; {@code null} when no archetype matches it.
	 */
	@Nullable
	ArchetypeProductId getArchetypeProductId();

	/**
	 * The classification input hash of the product the Classification was resolved from.
	 */
	String getInputHash();

	/**
	 * Whether the attempts it was resolved from disagree, so one of us should look at it.
	 */
	boolean isNeedsReview();

	LocalDateTime getResolvedAt();
}
