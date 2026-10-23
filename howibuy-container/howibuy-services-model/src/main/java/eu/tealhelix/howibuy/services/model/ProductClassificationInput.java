package eu.tealhelix.howibuy.services.model;

import java.util.List;
import java.util.Map;

import org.immutables.value.Value;

/**
 * What the Classification of a Retailer Product is made from, as stored: its names, characteristics and tags, with the
 * classification input hash of them.
 */
@Value.Immutable
public interface ProductClassificationInput {
	String getInputHash();

	/**
	 * The names of the product by their language code.
	 */
	Map<String, String> getNames();

	Map<String, String> getCharacteristics();

	List<String> getTags();
}
