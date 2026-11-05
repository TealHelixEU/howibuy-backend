package eu.tealhelix.howibuy.services.model;

import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import org.immutables.value.Value;

/**
 * One substitutable pair of the WP3 substitutability matrix, read as: the {@link #getFromCategoryId() from} category
 * may substitute for the {@link #getToCategoryId() to} category. Both are L2 categories of the SAFAD taxonomy.
 *
 * <p>
 * Only substitutable pairs exist; the absence of a pair means the substitution is not allowed at any degree. The
 * pair is directional, and a category is always substitutable for itself.
 */
@Value.Immutable
public interface Substitutability {
	ArchetypeCategoryId getFromCategoryId();

	ArchetypeCategoryId getToCategoryId();

	/**
	 * How readily the substitution may be made, 1 (barely) to 5 (freely). An Alternative is sought at each of the
	 * degrees 5, 4 and 3, among the categories substitutable at exactly that degree.
	 */
	short getDegree();
}
