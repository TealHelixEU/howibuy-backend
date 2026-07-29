package eu.tealhelix.sfc.jaxrs.v1;

import eu.tealhelix.sfc.v1.model.Category;
import eu.tealhelix.sfc.v1.types.CategoryId;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;

/**
 * A compass category with its text resolved for the requested language.
 */
public record CategoryDto(
		CategoryId id,
		SustainabilityDimension dimension,
		String name,
		String description,
		String videoUrl,
		String detailUrl
) {
	static CategoryDto from(Category category) {
		return new CategoryDto(
				category.getId(),
				category.getDimension(),
				category.getName(),
				category.getDescription(),
				category.getVideoUrl(),
				category.getDetailUrl());
	}
}
