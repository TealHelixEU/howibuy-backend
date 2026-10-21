package eu.tealhelix.catalogue.jaxrs.v1.productchanges;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A page of the Retailer Products that changed after a value of the change sequence.")
public record ProductChangePageDto(
		@Schema(description = "The value of the change sequence to read the next page after: that of the last product of the page, or the value the page was read after if it is empty.")
		long toSequence,
		@Schema(description = "The products, the lowest change sequence first.")
		List<ProductChangeDto> products
) {
}
