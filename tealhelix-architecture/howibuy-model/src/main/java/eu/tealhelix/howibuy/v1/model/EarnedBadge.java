package eu.tealhelix.howibuy.v1.model;

import eu.tealhelix.howibuy.v1.types.Badge;
import org.immutables.value.Value;

/**
 * A {@link Badge} a basket earns, with its label.
 */
@Value.Immutable
public interface EarnedBadge {
	Badge getBadge();

	/**
	 * A user-friendly name of the badge, translated in the requested language, if available.
	 */
	String getLabel();
}
