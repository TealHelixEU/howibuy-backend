package eu.tealhelix.catalogue.jaxrs.v1.ingestionruns;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "What the processing of a Feed found in it and did with it.")
public record IngestionRunCountsDto(
		@Schema(description = "The records read from the Feed.")
		int total,
		@Schema(description = "The records that passed validation.")
		int valid,
		@Schema(description = "The records rejected, each with its reason.")
		int rejected,
		@Schema(description = "The records new to the Catalogue.")
		int added,
		@Schema(description = "The records that changed a Retailer Product or its Listing.")
		int changed,
		@Schema(description = "The records that changed nothing.")
		int unchanged,
		@Schema(description = "The active Listings of the Catalogue that a Snapshot did not carry, which it takes off sale.")
		int missing,
		@Schema(description = "The Listings taken off sale.")
		int deactivated
) {
}
