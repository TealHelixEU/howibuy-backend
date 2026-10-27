package eu.tealhelix.howibuy.services.v1.alternatives;

import java.util.List;

import eu.tealhelix.howibuy.services.model.ArchetypeListing;

/**
 * The policy that picks the Retailer Product to recommend for an archetype, among the products of a Catalogue that
 * stand for it.
 */
public interface AlternativeProductSelector {
	/**
	 * @param listings The active Listings in the Catalogue of the products classified to the archetype, at least one
	 * @return The Listing of the product to recommend
	 */
	ArchetypeListing select(List<ArchetypeListing> listings);
}
