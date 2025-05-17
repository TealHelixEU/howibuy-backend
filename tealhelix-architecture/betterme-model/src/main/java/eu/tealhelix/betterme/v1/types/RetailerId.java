package eu.tealhelix.betterme.v1.types;

import eu.tealhelix.common.types.RepresentableAsString;

public interface RetailerId extends HasRetailerId, RepresentableAsString {
	@Override
	default RetailerId getId() {
		return this;
	}
}
