package eu.tealhelix.sfc.v1.types;

import java.util.UUID;

import eu.tealhelix.common.types.RepresentableAsString;

public interface CategoryId extends HasCategoryId, RepresentableAsString {
	@Override
	default CategoryId getId() {
		return this;
	}

	UUID asUuid();
}
