package eu.tealhelix.howibuy.jaxrs.v1.cataloguechanges;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "How far the Product Changes of the Ingestion have been applied.")
public record CatalogueChangeCursorResponse(
		@Schema(description = "The change sequence of the last change applied, 0 if none has been. The next page is read after it.")
		long lastSequence
) {
}
