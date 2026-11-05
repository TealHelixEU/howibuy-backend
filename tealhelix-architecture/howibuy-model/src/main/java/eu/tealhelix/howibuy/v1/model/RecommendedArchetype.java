package eu.tealhelix.howibuy.v1.model;

import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import org.immutables.value.Value;

/**
 * The archetype product an Alternative stands for, and the SAFAD taxonomy path it sits in.
 */
@Value.Immutable
public interface RecommendedArchetype {
	ArchetypeProductId getId();

	String getName();

	/**
	 * The SAFAD taxonomy path of the archetype, from the top-level category down to the leaf it hangs from. An
	 * Alternative may well come from a different branch than the assessed product's own, since substitutability reaches
	 * across categories, so the path says what kind of food is being offered.
	 */
	String getL1Category();

	String getL2Category();

	String getL3Category();
}
