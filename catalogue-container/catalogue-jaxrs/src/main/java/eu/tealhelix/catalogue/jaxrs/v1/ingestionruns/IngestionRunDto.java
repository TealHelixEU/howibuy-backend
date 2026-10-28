package eu.tealhelix.catalogue.jaxrs.v1.ingestionruns;

import java.time.LocalDateTime;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The processing of one Feed for one Catalogue.")
public record IngestionRunDto(
		@Schema(description = "The id of the run.")
		String id,
		@Schema(description = "The Feed the run processes.")
		String feedId,
		@Schema(description = "The Catalogue the Feed is processed for.")
		String catalogueId,
		@Schema(description = "The type of the Feed: SNAPSHOT, DELTA or DELETION.")
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
		@Schema(description = "What the processing found and did; null until it finishes.")
		IngestionRunCountsDto counts,
		@Schema(description = "Why the run failed; null otherwise.")
		String error,
		@Schema(description = "The run whose stored Feed this run processes again; null if this run received its Feed itself.")
		String replayOfRunId
) {
}
