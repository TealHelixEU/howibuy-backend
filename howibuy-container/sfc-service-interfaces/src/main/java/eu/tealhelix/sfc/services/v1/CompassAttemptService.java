package eu.tealhelix.sfc.services.v1;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.sfc.services.v1.types.AttemptAlreadyInProgressException;
import eu.tealhelix.sfc.services.v1.types.IncompleteCompassAttemptException;
import eu.tealhelix.sfc.services.v1.types.NoInProgressAttemptException;
import eu.tealhelix.sfc.services.v1.types.StabilityWindowActiveException;
import eu.tealhelix.sfc.v1.types.QuestionId;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import io.smallrye.mutiny.Uni;

/**
 * Records a user's answers on their current compass attempt and completes it. Answering starts the attempt on the first
 * answer and saves every answer immediately, so the user can pause and resume; re-answering a question overwrites the
 * previous choice. An attempt can also be started outright, for a user taking the compass again. Completion is a strictly
 * explicit act that freezes the attempt as an immutable record. Every
 * operation requires an authenticated end-user; a service account or unauthenticated caller is rejected.
 */
public interface CompassAttemptService {
	/**
	 * Sets the user's answer to {@code questionId} to {@code option} on their in-progress attempt, creating that
	 * attempt if they have none yet, and persists it immediately. If the user has no in-progress attempt and a
	 * previously completed attempt is still within its stability window, the write is refused with
	 * {@link StabilityWindowActiveException}; once the window has elapsed the answer starts a fresh, blank attempt.
	 */
	Uni<Void> answer(User user, QuestionId questionId, ScaleOption option);

	/**
	 * Starts a fresh, blank attempt for the user — the deliberate way to take the compass again, rather than waiting for
	 * an answer to bring an attempt into being. Fails with {@link AttemptAlreadyInProgressException} if they already have
	 * one in progress, or {@link StabilityWindowActiveException} if a previously completed attempt is still inside its
	 * stability window, carrying the moment that window ends.
	 */
	Uni<Void> startNewAttempt(User user);

	/**
	 * Completes the user's in-progress attempt: verifies every question is answered, then freezes it immutably,
	 * stamping the completion time. Fails with {@link IncompleteCompassAttemptException} carrying the unanswered
	 * question ids if any remain, or {@link NoInProgressAttemptException} if the user has no attempt in progress.
	 */
	Uni<Void> complete(User user);
}
