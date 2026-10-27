package eu.tealhelix.howibuy.services.v1.alternatives.impl;

import static java.util.Comparator.comparing;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.howibuy.services.model.ArchetypeListing;
import eu.tealhelix.howibuy.services.v1.alternatives.AlternativeProductSelector;

/**
 * The cheapest product, the one with the first retailer product id among those of the same price, so that the same
 * Listings always give the same answer.
 */
@ApplicationScoped
public class AlternativeProductSelectorImpl implements AlternativeProductSelector {
	private static final Comparator<ArchetypeListing> CHEAPEST_FIRST = comparing(ArchetypeListing::getPrice)
			.thenComparing(listing -> listing.getId().asUuid());

	@Override
	public ArchetypeListing select(List<ArchetypeListing> listings) {
		return Collections.min(listings, CHEAPEST_FIRST);
	}
}
