package eu.tealhelix.sfc.dao;

import java.util.Map;
import java.util.UUID;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.sfc.v1.types.QuestionId;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import io.smallrye.mutiny.Uni;

public interface AnswerDao {
	/**
	 * Sets the answer to {@code questionId} on {@code attemptId} to {@code option}, inserting it or overwriting the
	 * existing one.
	 */
	Uni<Void> upsert(ReactivePersistenceTxContext tx, UUID attemptId, QuestionId questionId, ScaleOption option);

	/**
	 * All answers recorded on {@code attemptId}, keyed by the question they answer. Empty when the attempt has none.
	 */
	Uni<Map<QuestionId, ScaleOption>> retrieveByAttempt(ReactivePersistenceContext em, UUID attemptId);
}
