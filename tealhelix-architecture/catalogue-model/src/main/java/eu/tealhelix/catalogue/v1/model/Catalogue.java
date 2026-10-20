package eu.tealhelix.catalogue.v1.model;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.common.v1.types.RetailerId;
import org.immutables.value.Value;

/**
 * One of a Retailer's e-shops, serving one country with its own assortment and prices.
 */
@Value.Immutable
public interface Catalogue {
	CatalogueId getId();

	RetailerId getRetailerId();

	/**
	 * The ISO 3166-1 alpha-2 code of the country served, e.g. {@code LT}.
	 */
	String getCountry();

	/**
	 * The ISO 639-1 code of the language every listed product must be named in, e.g. {@code lt}.
	 */
	String getPrimaryLanguage();

	/**
	 * Whether Feeds are accepted for this Catalogue.
	 */
	boolean isActive();
}
