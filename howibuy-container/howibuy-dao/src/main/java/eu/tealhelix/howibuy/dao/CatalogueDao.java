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

	/**
	 * Read whether a Catalogue of a Retailer is active, distinguishing an inactive Catalogue from one the Retailer does not
	 * own, or that does not exist at all.
	 *
	 * @param em          The persistence context
	 * @param retailerId  The Retailer
	 * @param catalogueId The Catalogue
	 * @return The active flag of the Catalogue, or {@code null} if the Retailer has no Catalogue with this id
	 */
	Uni<Boolean> findActiveFlag(ReactivePersistenceContext em, RetailerId retailerId, CatalogueId catalogueId);
}
