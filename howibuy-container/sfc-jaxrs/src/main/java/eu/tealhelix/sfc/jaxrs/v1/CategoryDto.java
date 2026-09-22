package eu.tealhelix.sfc.jaxrs.v1;

import eu.tealhelix.sfc.v1.model.Category;
import eu.tealhelix.sfc.v1.types.CategoryId;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A compass category with its text resolved for the requested language.
 */
@Schema(description = "A compass category with its texts resolved for the requested language.")
public record CategoryDto(
		@Schema(description = "The category, as the other reads refer to it.")
		CategoryId id,
		@Schema(description = "The sustainability dimension the category belongs to.")
		SustainabilityDimension dimension,
		@Schema(description = "The category name, localized.")
		String name,
		@Schema(description = "What the category is about, localized.")
		String description,
		@Schema(description = "A video explaining the category, if there is one for this language.")
		String videoUrl,
		@Schema(description = "A page to read more, if there is one for this language.")
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
