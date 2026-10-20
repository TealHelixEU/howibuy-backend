package eu.tealhelix.catalogue.v1.model;

import java.util.List;
import java.util.Map;

import eu.tealhelix.catalogue.v1.types.HasProductKey;
import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.common.v1.types.RetailerId;
import org.immutables.value.Value;

/**
 * The complete current state of a Retailer Product and its Listings, or the fact that it was removed, as read from the
 * change cursor of the Ingestion. Applying it makes the product exactly what it describes.
 */
@Value.Immutable
public interface ProductChange extends HasProductKey {
	/**
	 * The Retailer of the product. Together with the {@linkplain #getProductKey() product key} it identifies the product.
	 */
	RetailerId getRetailerId();

	/**
	 * The change sequence of the Ingestion that this state was stamped with.
	 */
	long getChangeSequence();

	/**
	 * Whether the product was removed. A removed product carries no names, characteristics, tags or Listings.
	 */
	boolean isRemoved();

	/**
	 * The classification input hash, computed by the Ingestion over the names, characteristics and tags; {@code null} for
	 * a removed product.
	 */
	@Nullable
	String getInputHash();

	/**
	 * The names of the product, by ISO 639-1 language code.
	 */
	Map<String, String> getNames();

	/**
	 * The characteristics of the product, the same in every language.
	 */
	Map<String, String> getCharacteristics();

	/**
	 * The tags of the product, the same in every language.
	 */
	List<String> getTags();

	/**
	 * Every Listing of the product, one per Catalogue it is on sale in.
	 */
	List<Listing> getListings();
}
