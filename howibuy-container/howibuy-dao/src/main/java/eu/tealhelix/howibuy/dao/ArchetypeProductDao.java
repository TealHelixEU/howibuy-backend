package eu.tealhelix.howibuy.dao;

import java.util.List;
import java.util.UUID;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.howibuy.services.model.ArchetypeProduct;
import io.smallrye.mutiny.Uni;

public interface ArchetypeProductDao {
	/**
	 * The archetype products belonging to the given (L3) category: the leaves of the SAFAD taxonomy and the candidates
	 * from which the archetype matching an assessed product is picked. Scoped by category id, since product names are
	 * unique only within a single category.
	 */
	Uni<List<ArchetypeProduct>> retrieveProductsInCategory(ReactivePersistenceContext em, UUID categoryId);
}
