package eu.tealhelix.catalogue.v1.types;

/**
 * Where an Ingestion Run stands.
 */
public enum IngestionRunStatus {
	/** The Feed is stored and waits to be processed. */
	RECEIVED,

	/** The Feed is being applied to its Catalogue. */
	PROCESSING,

	/** A Snapshot would take too much of its Catalogue off sale, and waits for an operator to confirm or discard it. */
	HELD,

	/** The Feed was applied. */
	DONE,

	/** The Feed could not be applied; the error tells why. */
	FAILED,

	/** An operator discarded the held run, so nothing was taken off sale. */
	DISCARDED
}
