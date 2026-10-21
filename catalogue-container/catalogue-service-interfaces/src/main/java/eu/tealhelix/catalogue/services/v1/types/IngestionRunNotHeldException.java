package eu.tealhelix.catalogue.services.v1.types;

import eu.tealhelix.catalogue.v1.types.HasIngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;

/**
 * Thrown when an operator confirms or discards an Ingestion Run that is not held, because it never was or because it has
 * been decided on already. Nothing is changed (mapped to HTTP 409).
 */
public class IngestionRunNotHeldException extends RuntimeException {
	public IngestionRunNotHeldException(HasIngestionRunId runId, IngestionRunStatus status) {
		super("The Ingestion Run is not held, status: " + status + ", runId: " + runId.getId().asString());
	}

	/**
	 * For a run that was held when it was read, but no longer was when it was stored.
	 */
	public IngestionRunNotHeldException(HasIngestionRunId runId) {
		super("The Ingestion Run is no longer held, runId: " + runId.getId().asString());
	}
}
