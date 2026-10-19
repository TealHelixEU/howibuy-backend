package eu.tealhelix.howibuy.v1.model;

import java.math.BigDecimal;
import java.util.Currency;

import eu.tealhelix.common.v1.types.CatalogueId;
import org.immutables.value.Value;

/**
 * A Retailer Product on sale in one Catalogue, at that Catalogue's price.
 */
@Value.Immutable
public interface Listing {
	CatalogueId getCatalogueId();

	BigDecimal getPrice();

	Currency getCurrency();

	/**
	 * Whether the Listing is in its Catalogue's latest Feeds. An inactive Listing is never offered as an Alternative.
	 */
	boolean isActive();
}
