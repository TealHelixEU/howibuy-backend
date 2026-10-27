package eu.tealhelix.howibuy.v1.model;

import java.math.BigDecimal;
import java.util.Currency;

import eu.tealhelix.catalogue.v1.types.HasProductKey;
import org.immutables.value.Value;

/**
 * A Retailer Product as the user finds it in a Catalogue: its name and that Catalogue's price.
 */
@Value.Immutable
public interface ListedProduct extends HasProductKey {
	String getName();

	/**
	 * The language of the name: the one asked for when the product has a name in it, else the Catalogue's primary
	 * language.
	 */
	String getLanguage();

	/**
	 * The price, without trailing zeros.
	 */
	BigDecimal getPrice();

	Currency getCurrency();
}
