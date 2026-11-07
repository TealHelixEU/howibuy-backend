package eu.tealhelix.sfc.jaxrs.v1;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import eu.tealhelix.sfc.services.v1.types.CompassOverview;
import eu.tealhelix.sfc.services.v1.types.Progress;
import eu.tealhelix.sfc.v1.types.AttemptStatus;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A single snapshot of where the user stands: overall progress and estimated completion time, the same per category,
 * the five localized scale labels, and the current attempt's state. {@code attemptStatus} is {@code null} when the user
 * has never started an attempt; {@code hasCompletedAttempt} tells whether they have ever completed one, true also while a
 * later one is in progress; {@code eligibleAt} is the moment the stability window of their completed attempt ends (so
 * the client can tell the user when a re-take becomes possible), and {@code null} when they have never completed an
 * attempt and while an attempt is in progress.
 */
@Schema(description = "A single snapshot of where the user stands in the compass, localized for the requested language.")
public record CompassOverviewDto(
		@Schema(description = "How many questions the user has answered across the whole compass, out of how many there are.")
		Progress overallProgress,
		@Schema(description = "Estimated seconds to answer every question of the compass.")
		long overallEstimatedSeconds,
		@Schema(description = "The same, per category, in the order the categories are asked.")
		List<CategoryOverviewDto> categories,
		@Schema(description = "The label to show for each option of the answer scale, localized.")
		Map<ScaleOption, String> scaleLabels,
		@Schema(description = "The state of the user's current attempt, or null when they have never started one.")
		AttemptStatus attemptStatus,
		@Schema(description = "Whether the user has ever completed an attempt; true also while a later attempt is in progress.")
		boolean hasCompletedAttempt,
		@Schema(description = "Whether the user may start a fresh attempt right now.")
		boolean eligibleToStartNewAttempt,
		@Schema(description = "When a completed attempt's stability window ends, the earliest a fresh attempt may start. Null when the user has never completed an attempt, and while an attempt is in progress.")
		LocalDateTime eligibleAt
) {
	static CompassOverviewDto from(CompassOverview overview) {
		return new CompassOverviewDto(
				overview.overallProgress(),
				overview.overallEstimatedSeconds(),
				overview.categories().stream().map(CategoryOverviewDto::from).toList(),
				overview.scaleLabels(),
				overview.attemptStatus().orElse(null),
				overview.hasCompletedAttempt(),
				overview.eligibleToStartNewAttempt(),
				overview.eligibleAt().orElse(null));
	}
}
