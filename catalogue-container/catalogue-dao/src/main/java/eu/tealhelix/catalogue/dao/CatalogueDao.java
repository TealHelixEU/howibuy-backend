package eu.tealhelix.catalogue.dao;

import java.util.List;

import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.types.HasCatalogueId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.v1.types.HasRetailerId;
import io.smallrye.mutiny.Uni;

public interface CatalogueDao {
	/**
	 * Read a Catalogue.
	 *
	 * @param em          The persistence context
	 * @param catalogueId The Catalogue
	 * @return The Catalogue, {@code null} if it does not exist
	 */
	Uni<Catalogue> find(ReactivePersistenceContext em, HasCatalogueId catalogueId);

	/**
	 * Read the active Catalogues of a Retailer, those that accept Feeds.
	 *
	 * @param em         The persistence context
	 * @param retailerId The Retailer
	 * @return The Catalogues, ordered by id; empty if the Retailer has none
	 */
	Uni<List<Catalogue>> findActive(ReactivePersistenceContext em, HasRetailerId retailerId);
}
