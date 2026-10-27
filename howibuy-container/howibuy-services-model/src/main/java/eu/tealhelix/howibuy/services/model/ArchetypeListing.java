package eu.tealhelix.howibuy.services.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;

import eu.tealhelix.catalogue.v1.types.HasProductKey;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.HasRetailerProductId;
import org.immutables.value.Value;

/**
 * An active Listing in a Catalogue of a Retailer Product classified to an archetype, one of the products that may stand
 * for that archetype when it is recommended.
 */
@Value.Immutable
public interface ArchetypeListing extends HasRetailerProductId, HasProductKey {
	/**
	 * The archetype the product is classified to.
	 */
	ArchetypeProductId getArchetypeProductId();

	BigDecimal getPrice();

	Currency getCurrency();

	/**
	 * The names of the product in the language asked for and in the Catalogue's primary language, by language, those of
	 * the two that the product has.
	 */
	Map<String, String> getNames();

	/**
	 * The primary language of the Catalogue.
	 */
	String getPrimaryLanguage();
}
