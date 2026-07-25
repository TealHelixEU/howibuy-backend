package eu.tealhelix.sfc.dao.impl;

import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.sfc.dao.AnswerDao;
import eu.tealhelix.sfc.dao.jpa.AnswerEntity;
import eu.tealhelix.sfc.dao.jpa.AnswerEntityId;
import eu.tealhelix.sfc.dao.jpa.AttemptEntity;
import eu.tealhelix.sfc.dao.jpa.QuestionEntity;
import eu.tealhelix.sfc.v1.types.QuestionId;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class AnswerDaoImpl implements AnswerDao {
	@Override
	public Uni<Void> upsert(ReactivePersistenceTxContext tx, UUID attemptId, QuestionId questionId, ScaleOption option) {
		return tx.find(AnswerEntity.class, new AnswerEntityId(attemptId, questionId.asUuid()))
				.flatMap(existing -> {
					if (existing != null) {
						existing.setValue(option.getValue());
						return Uni.createFrom().voidItem();
					} else {
						var answer = new AnswerEntity();
						answer.setAttempt(tx.getReference(AttemptEntity.class, attemptId));
						answer.setQuestion(tx.getReference(QuestionEntity.class, questionId.asUuid()));
						answer.setValue(option.getValue());
						return tx.persist(answer).replaceWithVoid();
					}
				});
	}
}
