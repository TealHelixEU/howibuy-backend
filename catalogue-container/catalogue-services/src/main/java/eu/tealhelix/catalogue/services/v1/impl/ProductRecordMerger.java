package eu.tealhelix.catalogue.services.v1.impl;

import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import eu.tealhelix.catalogue.feeds.FeedRecord;
import eu.tealhelix.catalogue.v1.model.ImmutableListing;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.Listing;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.v1.types.RetailerId;

/**
 * Applies a valid record of a Feed to the stored state of its Retailer Product. The names of the languages in the record
 * replace those languages' names, and the names in other languages are left alone. The characteristics and the tags are
 * replaced. The Listing of the Feed's Catalogue takes the price and the currency of the record and becomes active; the
 * other Listings are left alone.
 */
public final class ProductRecordMerger {
	/**
	 * @param product The new state of the product, with the change sequence of the stored state, or 0 for a new product
	 * @param outcome What changed
	 */
	public record Merge(ProductChange product, RecordOutcome outcome) {
	}

	private ProductRecordMerger() {
	}

	/**
	 * @param stored      The stored state of the product, {@code null} if it was never seen
	 * @param record      The record, which passed the {@link FeedRecordValidator}
	 * @param retailerId  The Retailer of the Feed
	 * @param catalogueId The Catalogue of the Feed
	 * @return The new state of the product and what changed
	 */
	public static Merge merge(ProductChange stored, FeedRecord record, RetailerId retailerId, CatalogueId catalogueId) {
		var names = new HashMap<String, String>();
		if (stored != null) names.putAll(stored.getNames());
		names.putAll(record.names());
		var characteristics = record.characteristics() != null ? record.characteristics() : Map.<String, String>of();
		var tags = record.tags() != null ? record.tags() : List.<String>of();
		var listing = ImmutableListing.builder()
				.catalogueId(catalogueId)
				.price(record.price())
				.currency(Currency.getInstance(record.currency()))
				.isActive(true)
				.build();
		var product = ImmutableProductChange.builder()
				.retailerId(retailerId)
				.productKey(new ProductKeyImpl(record.productKey()))
				.changeSequence(stored != null ? stored.getChangeSequence() : 0)
				.isRemoved(false)
				.inputHash(ClassificationInputHash.of(names, characteristics, tags))
				.names(names)
				.characteristics(characteristics)
				.tags(tags)
				.listings(withListing(stored != null ? stored.getListings() : List.of(), listing))
				.build();
		return new Merge(product, outcome(stored, product, catalogueId));
	}

	private static List<Listing> withListing(List<Listing> listings, Listing listing) {
		var result = new ArrayList<Listing>(listings.size() + 1);
		var replaced = false;
		for (var existing : listings) {
			if (existing.getCatalogueId().equals(listing.getCatalogueId())) {
				result.add(listing);
				replaced = true;
			} else {
				result.add(existing);
			}
		}
		if (!replaced) result.add(listing);
		return result;
	}

	private static RecordOutcome outcome(ProductChange stored, ProductChange product, CatalogueId catalogueId) {
		var storedListing = stored != null ? findListing(stored, catalogueId) : null;
		if (storedListing == null) {
			return RecordOutcome.ADDED;
		} else if (stored.isRemoved() || !sameContent(stored, product) || !sameListing(storedListing, findListing(product, catalogueId))) {
			return RecordOutcome.CHANGED;
		}
		return RecordOutcome.UNCHANGED;
	}

	private static boolean sameContent(ProductChange a, ProductChange b) {
		return a.getNames().equals(b.getNames()) && a.getCharacteristics().equals(b.getCharacteristics()) && a.getTags().equals(b.getTags());
	}

	/**
	 * The prices are compared by value, because the stored one has the scale of its column.
	 */
	private static boolean sameListing(Listing a, Listing b) {
		return a.isActive() == b.isActive() && a.getPrice().compareTo(b.getPrice()) == 0 && a.getCurrency().equals(b.getCurrency());
	}

	private static Listing findListing(ProductChange product, CatalogueId catalogueId) {
		return product.getListings().stream().filter(l -> Objects.equals(l.getCatalogueId(), catalogueId)).findFirst().orElse(null);
	}
}
