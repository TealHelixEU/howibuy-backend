package eu.tealhelix.catalogue.v1.types;

import java.util.UUID;

import eu.tealhelix.common.types.RepresentableAsString;

/**
 * Identifies one Ingestion Run, the receipt and processing of one Feed.
 */
public interface IngestionRunId extends HasIngestionRunId, RepresentableAsString {
	@Override
	default IngestionRunId getId() {
		return this;
	}

	UUID asUuid();
}
