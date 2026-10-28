package eu.tealhelix.catalogue.jaxrs.v1.feeds;

import java.time.LocalDateTime;
import java.util.List;

import eu.tealhelix.catalogue.jaxrs.v1.ingestionruns.IngestionRunDto;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A Feed as received, stored once, and the Ingestion Runs that process it: one per Catalogue it is for, and one per replay.")
public record FeedDto(
		@Schema(description = "The id of the Feed.")
		String id,
		@Schema(description = "SNAPSHOT, DELTA or DELETION.")
		String feedType,
		@Schema(description = "When the Feed was stored.")
		LocalDateTime receivedAt,
		@Schema(description = "The size of the Feed as received, in bytes.")
		long fileSize,
		@Schema(description = "The SHA-256 of the Feed as received, in lowercase hex.")
		String fileSha256,
		@Schema(description = "The runs, ordered by Catalogue and then by when they started, the runs not yet started last.")
		List<IngestionRunDto> runs
) {
}
