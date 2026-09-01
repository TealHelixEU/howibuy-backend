package eu.tealhelix.howibuy.v1.types;

/**
 * One of the four facets an archetype product is measured and scored on.
 * <p>
 * Deliberately <em>not</em> the Sustainable Food Compass's five {@code SustainabilityDimension} values. The compass
 * additionally elicits an economic dimension, which is scored nowhere because the product data carries no economic
 * indicator, and it names this enum's {@link #ENVIRONMENT} "ecological". The two sets are separate types on purpose;
 * whatever maps one to the other must do so explicitly. See ADR 0005.
 */
public enum ScoredSustainabilityDimension {
	ENVIRONMENT,
	ANIMAL_WELFARE,
	SOCIAL,
	/**
	 * Derived from the product's Nutri-Score rather than from indicators, so it alone has no single score of its own.
	 */
	HEALTH
}
