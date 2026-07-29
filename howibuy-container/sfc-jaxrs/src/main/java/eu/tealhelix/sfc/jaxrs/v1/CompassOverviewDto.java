package eu.tealhelix.sfc.jaxrs.v1;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import eu.tealhelix.sfc.services.v1.types.CompassOverview;
import eu.tealhelix.sfc.services.v1.types.Progress;
import eu.tealhelix.sfc.v1.types.AttemptStatus;
import eu.tealhelix.sfc.v1.types.ScaleOption;

/**
 * A single snapshot of where the user stands: overall progress and estimated completion time, the same per category,
 * the five localized scale labels, and the current attempt's state. {@code attemptStatus} is {@code null} when the user
 * has never started an attempt; {@code eligibleAt} is {@code null} until they have a completed attempt, after which it is
 * the moment its stability window ends (so the client can tell the user when a re-take becomes possible).
 */
public record CompassOverviewDto(
		Progress overallProgress,
		long overallEstimatedSeconds,
		List<CategoryOverviewDto> categories,
		Map<ScaleOption, String> scaleLabels,
		AttemptStatus attemptStatus,
		boolean eligibleToStartNewAttempt,
		LocalDateTime eligibleAt
) {
	static CompassOverviewDto from(CompassOverview overview) {
		return new CompassOverviewDto(
				overview.overallProgress(),
				overview.overallEstimatedSeconds(),
				overview.categories().stream().map(CategoryOverviewDto::from).toList(),
				overview.scaleLabels(),
				overview.attemptStatus().orElse(null),
				overview.eligibleToStartNewAttempt(),
				overview.eligibleAt().orElse(null));
	}
}
