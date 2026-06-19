package eu.tealhelix.howibuy.v1.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import eu.tealhelix.howibuy.v1.types.HasProductKey;
import org.immutables.value.Value;

/**
 * Information about a product to be fed to the sustainability assessment and substitution proposal algorithm.
 */
@Value.Immutable
public interface ProductData extends HasProductKey {
	/**
	 * The language of the provided information. All fields must be in this language.
	 */
	Locale getLanguage();

	/**
	 * Localized name of the product.
	 */
	String getName();

	/**
	 * Price of the product.
	 */
	BigDecimal getPrice();

	/**
	 * Three-letter code of the currency of the price.
	 */
	Currency getCurrency();

	/**
	 * A map of product characteristics to drive the classification. Both keys and values must be in the selected language.
	 */
	Map<String, String> getCharacteristics();

	/**
	 * A list of tags to drive the classification. All tags must be in the selected language.
	 */
	List<String> getTags();
}
