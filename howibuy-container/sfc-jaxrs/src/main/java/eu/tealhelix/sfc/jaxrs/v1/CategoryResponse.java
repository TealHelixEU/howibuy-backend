package eu.tealhelix.sfc.jaxrs.v1;

import java.util.UUID;

import eu.tealhelix.sfc.v1.model.Category;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;

/**
 * A compass category with its text resolved for the requested language.
 */
public record CategoryResponse(
		UUID id,
		SustainabilityDimension dimension,
		String name,
		String description,
		String videoUrl,
		String detailUrl
) {
	static CategoryResponse from(Category category) {
		return new CategoryResponse(
				category.getId(),
				category.getDimension(),
				category.getName(),
				category.getDescription(),
				category.getVideoUrl(),
				category.getDetailUrl());
	}
}
