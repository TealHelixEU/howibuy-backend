package eu.tealhelix.catalogue.v1.model;

import java.util.Set;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.HasCatalogueId;
import eu.tealhelix.common.v1.types.RetailerId;
import org.immutables.value.Value;

/**
 * One of a Retailer's e-shops, serving one country with its own assortment and prices.
 */
@Value.Immutable
public interface Catalogue extends HasCatalogueId {
	@Override
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
	 * The ISO 639-1 codes of the languages the products may be named in, the {@linkplain #getPrimaryLanguage() primary
	 * one} among them.
	 */
	Set<String> getAcceptedLanguages();

	/**
	 * The name of the adapter that reads the Feeds.
	 */
	String getAdapter();

	/**
	 * Whether Feeds are accepted for this Catalogue.
	 */
	boolean isActive();
}
