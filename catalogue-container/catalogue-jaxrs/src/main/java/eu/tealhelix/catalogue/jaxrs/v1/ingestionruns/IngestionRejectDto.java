package eu.tealhelix.catalogue.jaxrs.v1.ingestionruns;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A record of a Feed that was not applied, and why.")
public record IngestionRejectDto(
		@Schema(description = "Where the record is in the Feed, counting the records from 1.")
		long position,
		@Schema(description = "The product key of the record; null if it could not be read.")
		String productKey,
		@Schema(description = "Why the record was rejected.")
		String reason
) {
}
