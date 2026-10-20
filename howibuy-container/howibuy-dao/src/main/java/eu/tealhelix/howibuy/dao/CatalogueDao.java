package eu.tealhelix.howibuy.dao;

import java.util.Map;
import java.util.Set;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.v1.types.RetailerId;
import io.smallrye.mutiny.Uni;

public interface CatalogueDao {
	/**
	 * Read the Retailers owning some Catalogues.
	 *
	 * @param em           The persistence context
	 * @param catalogueIds The Catalogues
	 * @return The Retailer of each Catalogue found; the Catalogues that do not exist are missing from it
	 */
	Uni<Map<CatalogueId, RetailerId>> findRetailers(ReactivePersistenceContext em, Set<CatalogueId> catalogueIds);
}
