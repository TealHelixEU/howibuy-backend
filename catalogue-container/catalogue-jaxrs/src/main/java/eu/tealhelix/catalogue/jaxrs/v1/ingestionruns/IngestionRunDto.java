package eu.tealhelix.catalogue.jaxrs.v1.ingestionruns;

import java.time.LocalDateTime;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The receipt and processing of one Feed of a Catalogue.")
public record IngestionRunDto(
		@Schema(description = "The id of the run.")
		String id,
		@Schema(description = "The Catalogue the Feed is for.")
		String catalogueId,
		@Schema(description = "SNAPSHOT, DELTA or DELETION.")
		String feedType,
		@Schema(description = "RECEIVED, PROCESSING, HELD, DONE, FAILED or DISCARDED.")
		String status,
		@Schema(description = "The adapter that parses the Feed; null until processing starts.")
		String adapter,
		@Schema(description = "The version of the adapter; null until processing starts.")
		String adapterVersion,
		@Schema(description = "When the Feed was stored.")
		LocalDateTime receivedAt,
		@Schema(description = "When processing started; null until then.")
		LocalDateTime startedAt,
		@Schema(description = "When processing finished; null until then.")
		LocalDateTime finishedAt,
		@Schema(description = "The size of the Feed as received, in bytes.")
		long fileSize,
		@Schema(description = "The SHA-256 of the Feed as received, in lowercase hex.")
		String fileSha256,
		@Schema(description = "What the processing found and did; null until it finishes.")
		IngestionRunCountsDto counts,
		@Schema(description = "Why the run failed; null otherwise.")
		String error
) {
}
