package eu.tealhelix.howibuy.dao;

import java.util.List;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.howibuy.services.model.ArchetypeProduct;
import eu.tealhelix.howibuy.services.model.ArchetypeProductImpacts;
import eu.tealhelix.howibuy.v1.types.HasArchetypeCategoryId;
import io.smallrye.mutiny.Uni;

public interface ArchetypeProductDao {
	/**
	 * The archetype products belonging to the given (L3) category: the leaves of the SAFAD taxonomy and the candidates
	 * from which the archetype matching an assessed product is picked. Scoped by category id, since product names are
	 * unique only within a single category.
	 */
	Uni<List<ArchetypeProduct>> retrieveProductsInCategory(ReactivePersistenceContext em, HasArchetypeCategoryId category);

	/**
	 * Every archetype product with its measured impacts and its L2 category. The sustainability scoring normalises each
	 * dimension across the whole corpus, so it is meaningless on a subset and this is read as one — once, and cached
	 * for the lifetime of the application, as it is reference data that only changes on redeployment.
	 */
	Uni<List<ArchetypeProductImpacts>> retrieveAllWithImpacts(ReactivePersistenceContext em);
}
