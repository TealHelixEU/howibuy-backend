package eu.tealhelix.howibuy.scoring.v1;

/**
 * An Archetype Product drawn from the Pool of one degree of Substitutability, to be offered in place of the Reference
 * Product.
 *
 * @param product          The Archetype Product drawn
 * @param substitutability The degree of Substitutability of its category for the Reference Product's
 */
public record Alternative(ScoredProduct product, short substitutability) {
}
