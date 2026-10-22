package eu.tealhelix.howibuy.services.model;

import java.util.Set;

import eu.tealhelix.common.types.Nullable;
import org.immutables.value.Value;

/**
 * What the recording of a Classification Attempt checks the attempt against and resolves the Classification with.
 */
@Value.Immutable
public interface ProductToClassify {
	/**
	 * The classification input hash of the product's current names, characteristics and tags.
	 */
	String getInputHash();

	/**
	 * The languages the product is named in, the languages its Classification Attempts can be made in.
	 */
	Set<String> getLanguages();

	/**
	 * The language whose attempt wins when the attempts of the product disagree, the Retailer's; {@code null} orders
	 * them by language code alone.
	 */
	@Nullable
	String getClassificationLanguage();
}
