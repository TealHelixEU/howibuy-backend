package eu.tealhelix.sfc.services.v1.types;

import eu.tealhelix.sfc.v1.model.Category;

/**
 * One category's line on the compass overview: the localized {@link Category}, the user's {@link Progress} through it on
 * their current attempt, and the estimated seconds to answer all its questions.
 */
public record CategoryOverview(Category category, Progress progress, long estimatedSeconds) {
}
