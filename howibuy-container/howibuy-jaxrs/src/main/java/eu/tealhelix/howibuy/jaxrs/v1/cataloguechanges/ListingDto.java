package eu.tealhelix.howibuy.jaxrs.v1.cataloguechanges;

import java.math.BigDecimal;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A Retailer Product on sale in one Catalogue, at that Catalogue's price.")
public record ListingDto(
		@Schema(description = "The Catalogue, which must belong to the Retailer of the product.")
		String catalogueId,
		@Schema(description = "The price in the Catalogue.")
		BigDecimal price,
		@Schema(description = "ISO 4217 code of the currency of the price, e.g. 'EUR'.")
		String currency,
		@Schema(description = "Whether the Listing is in its Catalogue's latest Feeds. An inactive Listing is never offered as an Alternative.")
		boolean active
) {
}
