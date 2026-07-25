package eu.tealhelix.sfc.services.v1;

import java.util.UUID;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import io.smallrye.mutiny.Uni;

/**
 * Records a user's answers on their current compass attempt. Answering starts the attempt on the first answer and saves
 * every answer immediately, so the user can pause and resume; re-answering a question overwrites the previous choice.
 * Every operation requires an authenticated end-user; a service account or unauthenticated caller is rejected.
 */
public interface CompassAttemptService {
	/**
	 * Sets the user's answer to {@code questionId} to {@code option} on their in-progress attempt, creating that
	 * attempt if they have none yet, and persists it immediately.
	 */
	Uni<Void> answer(User user, UUID questionId, ScaleOption option);
}
