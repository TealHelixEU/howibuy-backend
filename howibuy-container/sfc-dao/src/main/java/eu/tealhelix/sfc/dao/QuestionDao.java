package eu.tealhelix.sfc.dao;

import java.util.List;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.CategoryId;
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
}
