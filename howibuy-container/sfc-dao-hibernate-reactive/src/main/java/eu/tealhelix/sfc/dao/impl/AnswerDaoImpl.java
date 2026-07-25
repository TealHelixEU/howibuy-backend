package eu.tealhelix.sfc.dao.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.Path;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.sfc.dao.AnswerDao;
import eu.tealhelix.sfc.dao.jpa.AnswerEntity;
import eu.tealhelix.sfc.dao.jpa.AnswerEntity_;
import eu.tealhelix.sfc.dao.jpa.AnswerEntityId;
import eu.tealhelix.sfc.dao.jpa.AttemptEntity;
import eu.tealhelix.sfc.dao.jpa.AttemptEntity_;
import eu.tealhelix.sfc.dao.jpa.QuestionEntity;
import eu.tealhelix.sfc.dao.jpa.QuestionEntity_;
import eu.tealhelix.sfc.v1.types.QuestionId;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import eu.tealhelix.sfc.v1.types.impl.QuestionIdImpl;
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

	@Override
	public Uni<Map<QuestionId, ScaleOption>> retrieveByAttempt(ReactivePersistenceContext em, UUID attemptId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var root = q.from(AnswerEntity.class);
		var question = root.get(AnswerEntity_.question);
		q.select(cb.tuple(
						question.get(QuestionEntity_.id),
						root.get(AnswerEntity_.value)))
				.where(cb.equal(root.get(AnswerEntity_.attempt).get(AttemptEntity_.id), attemptId));
		return em.createQuery(q).getResultList().map(list -> toAnswers(question, root, list));
	}

	private static Map<QuestionId, ScaleOption> toAnswers(Path<QuestionEntity> question, Path<AnswerEntity> answer, List<Tuple> tuples) {
		var answers = new LinkedHashMap<QuestionId, ScaleOption>();
		for (var tuple : tuples) {
			var questionId = new QuestionIdImpl(tuple.get(question.get(QuestionEntity_.id)).toString());
			answers.put(questionId, ScaleOption.fromValue(tuple.get(answer.get(AnswerEntity_.value))));
		}
		return answers;
	}
}
