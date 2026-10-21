package eu.tealhelix.catalogue.dao;

import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.types.HasCatalogueId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
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
}
