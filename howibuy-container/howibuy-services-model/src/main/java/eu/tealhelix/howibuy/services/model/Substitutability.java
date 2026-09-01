package eu.tealhelix.howibuy.services.model;

import java.util.UUID;

import org.immutables.value.Value;

/**
 * One substitutable pair of the WP3 substitutability matrix, read as: the {@link #getFromCategoryId() from} category
 * may substitute for the {@link #getToCategoryId() to} category. Both are L2 categories of the SAFAD taxonomy.
 *
 * <p>
 * Only substitutable pairs exist; the absence of a pair means the substitution is not allowed at any level. The
 * pair is directional, and a category is always substitutable for itself.
 */
@Value.Immutable
public interface Substitutability {
	UUID getFromCategoryId();

	UUID getToCategoryId();

	/**
	 * How readily the substitution may be made, 1 (barely) to 5 (freely). Compared against the cut-off of the
	 * configured substitutability level to decide whether the pair qualifies.
	 */
	short getDegree();
}
