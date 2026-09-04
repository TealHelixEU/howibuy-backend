package eu.tealhelix.howibuy.v1.types;

import java.util.UUID;

import eu.tealhelix.common.types.RepresentableAsString;

/**
 * Names one archetype product: a leaf of the SAFAD taxonomy, the thing an assessed product is matched to and the key
 * a recommended alternative is reported by.
 */
public interface ArchetypeProductId extends HasArchetypeProductId, RepresentableAsString {
	@Override
	default ArchetypeProductId getId() {
		return this;
	}

	UUID asUuid();
}
