package eu.tealhelix.howibuy.v1.model;

import eu.tealhelix.howibuy.v1.types.Badge;
import org.immutables.value.Value;

/**
 * A {@link Badge} of a series, with the number of products that awards it and whether the user holds it.
 */
@Value.Immutable
public interface BadgeLevel {
	Badge getBadge();

	/**
	 * A user-friendly name of the badge, translated in the requested language, if available.
	 */
	String getLabel();

	/**
	 * The number of purchased products above the threshold of the badge's dimension that awards the badge.
	 */
	int getRequiredProducts();

	/**
	 * Whether the user holds the badge. A held badge stays earned when its number has since been raised above the user's
	 * count; a badge whose lowered number the count already reaches is not earned until the next basket awards it.
	 */
	boolean isEarned();
}
