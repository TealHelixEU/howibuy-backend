package eu.tealhelix.sfc.jaxrs.v1;

import eu.tealhelix.sfc.services.v1.types.CategoryOverview;
import eu.tealhelix.sfc.services.v1.types.Progress;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * One category's line on the overview: the localized category, the user's progress through it, and the estimated
 * seconds to answer all its questions.
 */
@Schema(description = "One category's line on the overview.")
public record CategoryOverviewDto(
		@Schema(description = "The category this line is about.")
		CategoryDto category,
		@Schema(description = "How many of the category's questions the user has answered, out of how many there are.")
		Progress progress,
		@Schema(description = "Estimated seconds to answer all the questions of the category.")
		long estimatedSeconds) {
	static CategoryOverviewDto from(CategoryOverview overview) {
		return new CategoryOverviewDto(CategoryDto.from(overview.category()), overview.progress(), overview.estimatedSeconds());
	}
}
