package eu.tealhelix.howibuy.jaxrs.v1.cataloguechanges;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A page of Product Changes, as read from the change cursor of the Ingestion.")
public record CatalogueChangesRequest(
		@Schema(description = "The change sequence the page was read after. It must be the cursor of this application, or the page is refused.")
		Long fromSequence,
		@Schema(description = "The change sequence the cursor continues from after the page.")
		Long toSequence,
		@Schema(description = "The changes, each with a change sequence after fromSequence and up to toSequence, applied in the order sent.")
		List<ProductChangeDto> products
) {
}
