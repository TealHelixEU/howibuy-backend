package eu.tealhelix.catalogue.services.v1.types;

import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;

/**
 * Thrown when an operator replays an Ingestion Run that has not finished, or a Snapshot that a later Snapshot of its
 * Catalogue supersedes. No run is created (mapped to HTTP 409).
 */
public class IngestionRunNotReplayableException extends RuntimeException {
	private IngestionRunNotReplayableException(String message) {
		super(message);
	}

	/**
	 * For a run still waiting, processing or held.
	 */
	public static IngestionRunNotReplayableException unfinished(IngestionRunId runId, IngestionRunStatus status) {
		return new IngestionRunNotReplayableException("Only a finished Ingestion Run is replayed, status: " + status + ", runId: " + runId.asString());
	}

	/**
	 * For a Snapshot whose replay would take off sale the Listings that the later Snapshot carries.
	 */
	public static IngestionRunNotReplayableException superseded(IngestionRunId runId) {
		return new IngestionRunNotReplayableException("A later Snapshot of the Catalogue supersedes the Ingestion Run, runId: " + runId.asString());
	}
}
