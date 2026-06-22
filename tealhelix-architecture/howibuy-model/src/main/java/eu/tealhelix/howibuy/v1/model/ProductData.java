package eu.tealhelix.howibuy.v1.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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

	static String toLogString(ProductData pd) {
		if (pd == null) return "null";
		var sb = new StringBuilder("ProductData{");
		sb.append(pd.getProductKey() != null ? '"' + pd.getProductKey().asString() + '"' : "null");
		sb.append(", language=").append(pd.getLanguage());
		sb.append(", name=").append(pd.getName() != null ? '"' + pd.getName() + '"' : "null");
		sb.append(", price=").append(pd.getPrice());
		sb.append(", currency=").append(pd.getCurrency());
		sb.append(", characteristics=");
		if (pd.getCharacteristics() == null) {
			sb.append("null");
		} else {
			sb.append(pd.getCharacteristics().entrySet().stream().map(e -> '"' + e.getKey() + "\"=" + (e.getValue() != null ? '"' + e.getValue() + '"' : "null")).collect(Collectors.joining(", ", "{", "}")));
		}
		sb.append(", tags=");
		if (pd.getTags() == null) {
			sb.append("null");
		} else {
			sb.append(pd.getTags().stream().map(t -> '"' + t + '"').collect(Collectors.joining(", ", "[", "]")));
		}
		return sb.append('}').toString();
	}
}
