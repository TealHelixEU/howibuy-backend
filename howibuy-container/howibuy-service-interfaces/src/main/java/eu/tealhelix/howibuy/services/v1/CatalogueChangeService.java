package eu.tealhelix.howibuy.services.v1;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.services.v1.types.ProductChangePage;
import io.smallrye.mutiny.Uni;

/**
 * The receiving end of the Product Changes that the Catalogue Sync carries over from the Ingestion. HowiBuy owns the
 * cursor: it records how far the changes have been applied in the same transaction as the changes themselves.
 */
public interface CatalogueChangeService {
	/**
	 * Read how far the changes have been applied.
	 *
	 * @param caller The caller, who must be the Catalogue Sync
	 * @return The change sequence of the last change applied, 0 if none has been
	 */
	Uni<Long> readCursor(User caller);

	/**
	 * Apply a page of changes and advance the cursor to its end, all or nothing. Each Retailer Product becomes exactly what
	 * its change describes, so applying the same page again leaves the same state.
	 *
	 * @param caller The caller, who must be the Catalogue Sync
	 * @param page   The page
	 * @return A {@code Uni} completing when the page has been applied, or failing with a
	 *         {@link eu.tealhelix.howibuy.services.v1.types.ChangeCursorConflictException} if the page does not start at
	 *         the cursor
	 */
	Uni<Void> apply(User caller, ProductChangePage page);
}
