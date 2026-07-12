package eu.tealhelix.howibuy.services.v1.enrichment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.howibuy.dao.FoodTermDao;
import eu.tealhelix.howibuy.services.model.FoodTerm;
import io.smallrye.mutiny.Uni;

/**
 * The food-term glossary as seen by the assessment flow: given a product's language and name, it returns the glossary
 * terms occurring in the name. The per-language term set is loaded from the database once and cached for the lifetime
 * of the application, since it is reference data that only changes on redeployment.
 */
@ApplicationScoped
public class FoodTermGlossary {
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final FoodTermDao foodTermDao;
	private final Map<String, Uni<List<FoodTerm>>> termsByLanguage = new ConcurrentHashMap<>();

	@Inject
	public FoodTermGlossary(ReactivePersistenceContextFactory persistenceContextFactory, FoodTermDao foodTermDao) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.foodTermDao = foodTermDao;
	}

	public Uni<List<FoodTerm>> match(String language, String productName) {
		var normalizer = TextNormalizers.forLanguage(language);
		return termsOf(language).map(terms -> FoodTermMatcher.match(terms, productName, normalizer));
	}

	private Uni<List<FoodTerm>> termsOf(String language) {
		return termsByLanguage.computeIfAbsent(
				language,
				lang -> persistenceContextFactory
						.withoutTransaction(em -> foodTermDao.retrieveByLanguage(em, lang))
						.memoize().indefinitely());
	}
}
