package eu.tealhelix.howibuy.dao;

import java.util.List;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.howibuy.services.model.FoodTerm;
import io.smallrye.mutiny.Uni;

public interface FoodTermDao {
	/**
	 * The glossary food terms of the given language (ISO 639-1 code), each carrying its English canonical name,
	 * description and optional category hint. Used to enrich a retailer product name before AI classification.
	 */
	Uni<List<FoodTerm>> retrieveByLanguage(ReactivePersistenceContext em, String language);
}
