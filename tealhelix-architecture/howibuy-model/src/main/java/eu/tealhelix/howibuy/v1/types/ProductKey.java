package eu.tealhelix.howibuy.v1.types;

import eu.tealhelix.common.types.RepresentableAsString;

public interface ProductKey extends HasProductKey, RepresentableAsString {
	@Override
	default ProductKey getProductKey() {
		return this;
	}
}
