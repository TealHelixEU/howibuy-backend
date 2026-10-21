package eu.tealhelix.catalogue.services.v1;

import eu.tealhelix.catalogue.services.v1.types.ProductChangePage;
import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.common.v1.model.User;
import io.smallrye.mutiny.Uni;

/**
 * Tells the Catalogue Sync which Retailer Products changed after a value of the change sequence, each in its complete
 * current state. Every change of a product or its Listings stamps the product with the next value of the sequence, so a
 * product changed several times between two reads is read once, at its latest value.
 */
public interface ProductChangeService {
	/**
	 * Read a page of the products that changed after a value of the change sequence. The next page is read after the
	 * page's {@link ProductChangePage#toSequence() toSequence}; the page after the last change is empty.
	 *
	 * @param caller The caller, the Catalogue Sync
	 * @param after  The value of the change sequence, not negative; 0 reads from the first change
	 * @param limit  How many products to read at most, from 1 to the configured maximum; {@code null} for the configured
	 *               default
	 * @return The page
	 * @throws eu.tealhelix.common.types.authorization.NotAuthenticatedException If the caller is not authenticated
	 * @throws eu.tealhelix.common.types.authorization.NotAuthorizedException    If the caller is not the Catalogue Sync
	 * @throws eu.tealhelix.common.types.validation.BadInputValueException       If the value or the limit is out of range
	 */
	Uni<ProductChangePage> findChanges(User caller, long after, @Nullable Integer limit);
}
