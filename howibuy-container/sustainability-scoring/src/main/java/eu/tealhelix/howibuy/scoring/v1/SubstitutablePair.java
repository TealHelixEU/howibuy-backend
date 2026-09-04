package eu.tealhelix.howibuy.scoring.v1;

import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;

/**
 * One cell of the WP3 substitutability matrix, read as: the {@code from} category may substitute for the {@code to}
 * category, as readily as {@code degree} says. Both ends are L2 categories of the SAFAD taxonomy.
 * <p>
 * Only substitutable pairs exist. A pair that is absent is not substitutable at any level.
 */
public record SubstitutablePair(ArchetypeCategoryId fromCategoryId, ArchetypeCategoryId toCategoryId, short degree) {
}
