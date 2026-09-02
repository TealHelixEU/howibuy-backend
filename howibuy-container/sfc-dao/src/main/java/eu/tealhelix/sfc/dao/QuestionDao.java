package eu.tealhelix.sfc.dao;

import java.util.List;
import java.util.Map;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.CategoryId;
import eu.tealhelix.sfc.v1.types.QuestionId;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;
import io.smallrye.mutiny.Uni;

public interface QuestionDao {
	/**
	 * The questions of a single category, with their prompt resolved for the given language (ISO 639-1 code), in
	 * position order.
	 */
	Uni<List<Question>> retrieveByCategoryAndLanguage(ReactivePersistenceContext em, CategoryId categoryId, String language);

	/**
	 * All questions across all categories, with their prompt resolved for the given language (ISO 639-1 code). Ordered
	 * so that questions of the same category are adjacent (by dimension, then position), matching the category order of
	 * {@link CategoryDao#retrieveByLanguage}.
	 */
	Uni<List<Question>> retrieveByLanguage(ReactivePersistenceContext em, String language);

	/**
	 * The ids of every question across all categories, in a deterministic order (by category, then position). Used to
	 * check an attempt for completeness — that every question has an answer — independently of language.
	 */
	Uni<List<QuestionId>> retrieveAllIds(ReactivePersistenceContext em);

	/**
	 * The dimension each question addresses, by way of the category it belongs to. Language-independent, because a
	 * question's dimension has nothing to do with the words it is asked in.
	 */
	Uni<Map<QuestionId, SustainabilityDimension>> retrieveDimensionsByQuestion(ReactivePersistenceContext em);
}
