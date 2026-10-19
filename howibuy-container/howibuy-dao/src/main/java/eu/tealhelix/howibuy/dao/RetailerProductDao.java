package eu.tealhelix.howibuy.dao;

import java.util.List;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.howibuy.v1.model.ProductChange;
import io.smallrye.mutiny.Uni;

public interface RetailerProductDao {
	/**
	 * Make each Retailer Product exactly what its change describes: create or update it, its names and its Listings, so
	 * that the names and Listings the change does not carry are deleted, or delete the product if the change says it was
	 * removed. Applying the same changes again leaves the same state.
	 *
	 * @param tx      The persistence context
	 * @param changes The changes, applied in their order
	 * @return A {@code Uni} completing when the changes have been applied
	 */
	Uni<Void> apply(ReactivePersistenceTxContext tx, List<ProductChange> changes);
}
