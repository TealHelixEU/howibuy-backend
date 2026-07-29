package eu.tealhelix.sfc.jaxrs.v1;

import eu.tealhelix.sfc.services.v1.types.CategoryOverview;
import eu.tealhelix.sfc.services.v1.types.Progress;

/**
 * One category's line on the overview: the localized category, the user's progress through it, and the estimated
 * seconds to answer all its questions.
 */
public record CategoryOverviewDto(CategoryDto category, Progress progress, long estimatedSeconds) {
	static CategoryOverviewDto from(CategoryOverview overview) {
		return new CategoryOverviewDto(CategoryDto.from(overview.category()), overview.progress(), overview.estimatedSeconds());
	}
}
