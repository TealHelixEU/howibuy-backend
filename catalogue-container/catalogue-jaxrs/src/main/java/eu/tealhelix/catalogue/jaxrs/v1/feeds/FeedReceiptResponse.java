package eu.tealhelix.catalogue.jaxrs.v1.feeds;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The Ingestion Run of a Feed just received, and where to follow it.")
public record FeedReceiptResponse(
		@Schema(description = "The id of the Ingestion Run.")
		String id,
		@Schema(description = "The status of the run, RECEIVED.")
		String status,
		@Schema(description = "The URL that tells how the run stands, also given in the Location header.")
		String statusUrl
) {
}
