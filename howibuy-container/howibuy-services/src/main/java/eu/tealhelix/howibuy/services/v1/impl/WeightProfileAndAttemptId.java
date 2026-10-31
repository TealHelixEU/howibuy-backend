package eu.tealhelix.howibuy.services.v1.impl;

import java.util.UUID;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.WeightProfile;

/**
 * A user's weighting profile and the compass attempt it was derived from, {@code null} when the profile is the
 * scientific one.
 */
public record WeightProfileAndAttemptId(WeightProfile profile, @Nullable UUID compassAttemptId) {
}
