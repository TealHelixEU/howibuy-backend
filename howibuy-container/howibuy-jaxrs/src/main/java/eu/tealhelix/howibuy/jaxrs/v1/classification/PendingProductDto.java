package eu.tealhelix.howibuy.jaxrs.v1.classification;

import java.util.Set;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A Retailer Product waiting to be classified: it has no Classification, or its Classification was resolved from other names, characteristics or tags than its current ones.")
public record PendingProductDto(
		@Schema(description = "The product.")
		String retailerProductId,
		@Schema(description = "The classification input hash of the product's current names, characteristics and tags. An attempt is recorded with it, and refused if the product has changed since.")
		String inputHash,
		@Schema(description = "ISO 639-1 codes of the languages the product is named in, in alphabetical order: the languages its attempts can be made in.")
		Set<String> languages
) {
}
