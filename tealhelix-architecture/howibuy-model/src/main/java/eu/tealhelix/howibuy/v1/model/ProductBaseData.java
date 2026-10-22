package eu.tealhelix.howibuy.v1.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Information about a product in one language, from which it can be matched to an Archetype Product.
 */
public interface ProductBaseData {
	/**
	 * The language of the provided information. All fields must be in this language.
	 */
	Locale getLanguage();

	/**
	 * Localized name of the product.
	 */
	String getName();

	/**
	 * A map of product characteristics to drive the classification. Both keys and values must be in the selected language.
	 */
	Map<String, String> getCharacteristics();

	/**
	 * A list of tags to drive the classification. All tags must be in the selected language.
	 */
	List<String> getTags();
}
