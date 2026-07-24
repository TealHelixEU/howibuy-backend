package eu.tealhelix.sfc.dao;

import java.util.List;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.sfc.v1.model.Category;
import io.smallrye.mutiny.Uni;

public interface CategoryDao {
	/**
	 * All compass categories with their text resolved for the given language (ISO 639-1 code). Categories are
	 * independent and unordered; they are returned in a stable order (by dimension, then id).
	 */
	Uni<List<Category>> retrieveByLanguage(ReactivePersistenceContext em, String language);
}
