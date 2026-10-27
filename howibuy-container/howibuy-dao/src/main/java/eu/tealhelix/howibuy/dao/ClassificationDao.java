package eu.tealhelix.howibuy.dao;

import java.util.List;
import java.util.Set;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.howibuy.services.model.ProductClassificationInput;
import eu.tealhelix.howibuy.services.model.ProductToAssess;
import eu.tealhelix.howibuy.services.model.ProductToClassify;
import eu.tealhelix.howibuy.v1.model.Classification;
import eu.tealhelix.howibuy.v1.model.ClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.ClassificationToReview;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.Classifier;
import eu.tealhelix.howibuy.v1.types.HasRetailerProductId;
import io.smallrye.mutiny.Uni;

public interface ClassificationDao {
	/**
	 * Find the products waiting to be classified: those without a Classification, those whose Classification was
	 * resolved from another input hash than their current one, and those whose Classification was reopened.
	 *
	 * @param em    The persistence context
	 * @param limit The most products to find
	 * @return The products, the one whose current state came with the oldest Product Change first
	 */
	Uni<List<PendingProduct>> findPending(ReactivePersistenceContext em, int limit);

	/**
	 * Find the products whose Classification needs review, leaving out those changed since it was resolved and those
	 * whose Classification was reopened, which are pending again.
	 *
	 * @param em    The persistence context
	 * @param limit The most products to find
	 * @return The products, the one whose Classification was resolved longest ago first
	 */
	Uni<List<ClassificationToReview>> findToReview(ReactivePersistenceContext em, int limit);

	/**
	 * Find the products of the Retailer of a Catalogue by their keys, with their Classifications. A product is found
	 * whatever its Listings say, even when it is not listed in this Catalogue at all: the Classification belongs to the
	 * product.
	 *
	 * @param em          The persistence context
	 * @param catalogueId The Catalogue
	 * @param productKeys The keys of the products
	 * @return The products found, in no particular order; a key that names no product of the Retailer has none
	 */
	Uni<List<ProductToAssess>> findProductsToAssess(ReactivePersistenceContext em, CatalogueId catalogueId, Set<ProductKey> productKeys);

	/**
	 * Find the archetypes a Catalogue stocks: those that a product with an active Listing in it is classified to,
	 * whatever the state of its Classification.
	 *
	 * @param em          The persistence context
	 * @param catalogueId The Catalogue
	 * @return The archetypes, none for an unknown Catalogue
	 */
	Uni<Set<ArchetypeProductId>> findStockedArchetypes(ReactivePersistenceContext em, CatalogueId catalogueId);

	/**
	 * Find a product to record a Classification Attempt of, and lock its row until the transaction ends, so that the
	 * attempts recorded for it at the same time, and the Product Changes applied to it, happen one after the other.
	 *
	 * @param tx        The persistence context
	 * @param productId The product
	 * @return The product, {@code null} if there is no product with this id
	 */
	Uni<ProductToClassify> findLockedProductToClassify(ReactivePersistenceTxContext tx, HasRetailerProductId productId);

	/**
	 * @param em        The persistence context
	 * @param productId The product
	 * @return What the Classification of the product is made from, {@code null} if there is no product with this id
	 */
	Uni<ProductClassificationInput> findClassificationInput(ReactivePersistenceContext em, HasRetailerProductId productId);

	/**
	 * @param tx        The persistence context
	 * @param productId The product classified
	 * @param attempt   The attempt
	 * @return A {@code Uni} completing when the attempt has been recorded
	 */
	Uni<Void> recordAttempt(ReactivePersistenceTxContext tx, HasRetailerProductId productId, ClassificationAttempt attempt);

	/**
	 * @param em        The persistence context
	 * @param productId The product classified
	 * @param inputHash The classification input hash the attempts were made from
	 * @return The attempts of the product made from this input hash, in no particular order
	 */
	Uni<List<ClassificationAttempt>> findAttempts(ReactivePersistenceContext em, HasRetailerProductId productId, String inputHash);

	/**
	 * Make the Classification of a product the given one, whether the product had one or not. A reopened
	 * Classification is no longer reopened.
	 *
	 * @param tx             The persistence context
	 * @param productId      The product classified
	 * @param classification The Classification
	 * @return A {@code Uni} completing when the Classification has been saved
	 */
	Uni<Void> saveClassification(ReactivePersistenceTxContext tx, HasRetailerProductId productId, Classification classification);

	/**
	 * Reopen the Classifications that came from a classifier version, which makes their products pending. A
	 * Classification came from the versions of the latest attempts of the classifier that decided it, one per language,
	 * made from its input hash.
	 *
	 * @param tx                The persistence context
	 * @param classifier        The classifier
	 * @param classifierVersion The version of the classifier
	 * @return How many Classifications were reopened
	 */
	Uni<Integer> reopen(ReactivePersistenceTxContext tx, Classifier classifier, String classifierVersion);
}
