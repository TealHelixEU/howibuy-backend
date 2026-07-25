package eu.tealhelix.sfc.dao.impl;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.Path;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.sfc.dao.QuestionDao;
import eu.tealhelix.sfc.dao.jpa.CategoryEntity;
import eu.tealhelix.sfc.dao.jpa.CategoryEntity_;
import eu.tealhelix.sfc.dao.jpa.QuestionEntity;
import eu.tealhelix.sfc.dao.jpa.QuestionEntity_;
import eu.tealhelix.sfc.dao.jpa.QuestionTextEntity;
import eu.tealhelix.sfc.dao.jpa.QuestionTextEntity_;
import eu.tealhelix.sfc.v1.model.ImmutableQuestion;
import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.CategoryId;
import eu.tealhelix.sfc.v1.types.impl.CategoryIdImpl;
import eu.tealhelix.sfc.v1.types.impl.QuestionIdImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class QuestionDaoImpl implements QuestionDao {
	@Override
	public Uni<List<Question>> retrieveByCategoryAndLanguage(ReactivePersistenceContext em, CategoryId categoryId, String language) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var root = q.from(QuestionTextEntity.class);
		var question = root.get(QuestionTextEntity_.question);
		var category = question.get(QuestionEntity_.category);
		q.select(cb.tuple(
						question.get(QuestionEntity_.id),
						category.get(CategoryEntity_.id),
						question.get(QuestionEntity_.position),
						root.get(QuestionTextEntity_.text)))
				.where(cb.and(
						cb.equal(root.get(QuestionTextEntity_.lang), language),
						cb.equal(category.get(CategoryEntity_.id), categoryId.asUuid())))
				.orderBy(cb.asc(question.get(QuestionEntity_.position)), cb.asc(question.get(QuestionEntity_.id)));
		return em.createQuery(q).getResultList().map(list -> toQuestions(question, category, root, list));
	}

	@Override
	public Uni<List<Question>> retrieveByLanguage(ReactivePersistenceContext em, String language) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var root = q.from(QuestionTextEntity.class);
		var question = root.get(QuestionTextEntity_.question);
		var category = question.get(QuestionEntity_.category);
		q.select(cb.tuple(
						question.get(QuestionEntity_.id),
						category.get(CategoryEntity_.id),
						question.get(QuestionEntity_.position),
						root.get(QuestionTextEntity_.text)))
				.where(cb.equal(root.get(QuestionTextEntity_.lang), language))
				.orderBy(
						cb.asc(category.get(CategoryEntity_.dimension)),
						cb.asc(category.get(CategoryEntity_.id)),
						cb.asc(question.get(QuestionEntity_.position)));
		return em.createQuery(q).getResultList().map(list -> toQuestions(question, category, root, list));
	}

	private static List<Question> toQuestions(Path<QuestionEntity> question, Path<CategoryEntity> category, Path<QuestionTextEntity> text, List<Tuple> tuples) {
		return tuples.stream().map(t -> toQuestion(question, category, text, t)).toList();
	}

	private static Question toQuestion(Path<QuestionEntity> question, Path<CategoryEntity> category, Path<QuestionTextEntity> text, Tuple tuple) {
		return ImmutableQuestion.builder()
				.id(new QuestionIdImpl(tuple.get(question.get(QuestionEntity_.id)).toString()))
				.categoryId(new CategoryIdImpl(tuple.get(category.get(CategoryEntity_.id)).toString()))
				.position(tuple.get(question.get(QuestionEntity_.position)))
				.text(tuple.get(text.get(QuestionTextEntity_.text)))
				.build();
	}
}
