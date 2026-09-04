package eu.tealhelix.howibuy.v1.types;

import java.util.UUID;

import eu.tealhelix.common.types.RepresentableAsString;

/**
 * Names one node of the SAFAD taxonomy, at any of its three levels: the assessment descends L1 to L3 by a node's
 * children, and substitutability is decided between L2 nodes.
 */
public interface ArchetypeCategoryId extends HasArchetypeCategoryId, RepresentableAsString {
	@Override
	default ArchetypeCategoryId getId() {
		return this;
	}

	UUID asUuid();
}
