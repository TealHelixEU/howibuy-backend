package eu.tealhelix.catalogue.jaxrs.v1.productchanges;

import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The complete current state of a Retailer Product and its Listings, or the fact that it was removed. Applying it makes the product exactly what it describes: names and Listings it does not carry are deleted.")
public record ProductChangeDto(
		@Schema(description = "The Retailer of the product, i.e. the id of its service account in the IDM.")
		String retailerId,
		@Schema(description = "The Retailer's own id of the product. Together with the Retailer it identifies the product.")
		String productKey,
		@Schema(description = "The value of the change sequence that the product was last stamped with.")
		long changeSequence,
		@Schema(description = "Whether the product was removed. A removed product carries nothing but its identity and change sequence.")
		boolean removed,
		@Schema(description = "The classification input hash, computed over the names, characteristics and tags; null if removed.")
		String inputHash,
		@Schema(description = "The names of the product, by ISO 639-1 language code; null if removed.")
		Map<String, String> names,
		@Schema(description = "The characteristics of the product, the same in every language; null if removed.")
		Map<String, String> characteristics,
		@Schema(description = "The tags of the product, the same in every language; null if removed.")
		List<String> tags,
		@Schema(description = "Every Listing of the product, one per Catalogue it is on sale in; null if removed.")
		List<ListingDto> listings
) {
}
