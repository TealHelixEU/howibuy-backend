package eu.tealhelix.howibuy.jaxrs.v1.cataloguechanges;

import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The complete current state of a Retailer Product and its Listings, or the fact that it was removed. The product becomes exactly what it describes: names and Listings it does not carry are deleted.")
public record ProductChangeDto(
		@Schema(description = "The Retailer of the product, i.e. the id of its service account in the IDM.")
		String retailerId,
		@Schema(description = "The Retailer's own id of the product. Together with the Retailer it identifies the product.")
		String productKey,
		@Schema(description = "The change sequence of the Ingestion that this state was stamped with.")
		Long changeSequence,
		@Schema(description = "Whether the product was removed. A removed product is deleted, and carries nothing but its identity and change sequence.")
		boolean removed,
		@Schema(description = "The classification input hash, computed by the Ingestion over the names, characteristics and tags. Required unless removed.")
		String inputHash,
		@Schema(description = "The names of the product, by ISO 639-1 language code.")
		Map<String, String> names,
		@Schema(description = "The characteristics of the product, the same in every language.")
		Map<String, String> characteristics,
		@Schema(description = "The tags of the product, the same in every language.")
		List<String> tags,
		@Schema(description = "Every Listing of the product, one per Catalogue it is on sale in.")
		List<ListingDto> listings
) {
}
