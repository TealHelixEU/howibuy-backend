package eu.tealhelix.sfc.services.v1.types;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import eu.tealhelix.sfc.v1.types.AttemptStatus;
import eu.tealhelix.sfc.v1.types.ScaleOption;

/**
 * A single snapshot of where a user stands on the compass, measured against their current attempt: overall
 * {@link Progress} and estimated completion time, the same broken down per {@link CategoryOverview category}, the five
 * localized {@link ScaleOption} labels, and the attempt's state.
 * <p>
 * {@link #attemptStatus()} is empty when the user has never started an attempt. {@link #eligibleToStartNewAttempt()} is
 * true only when they may begin a fresh attempt right now — they have none in progress and either never completed one or
 * its stability window has elapsed. {@link #eligibleAt()} is the moment that stability window ends, so the user can be
 * told when a re-take becomes possible; it is present when their current attempt is a completed one and absent while an
 * attempt is in progress.
 */
public record CompassOverview(
		Progress overallProgress,
		long overallEstimatedSeconds,
		List<CategoryOverview> categories,
		Map<ScaleOption, String> scaleLabels,
		Optional<AttemptStatus> attemptStatus,
		boolean eligibleToStartNewAttempt,
		Optional<LocalDateTime> eligibleAt
) {
}
