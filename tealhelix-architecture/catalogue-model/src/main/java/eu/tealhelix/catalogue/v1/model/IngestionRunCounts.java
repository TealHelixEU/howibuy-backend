package eu.tealhelix.catalogue.v1.model;

import org.immutables.value.Value;

/**
 * What the processing of a Feed found in it and did with it.
 */
@Value.Immutable
public interface IngestionRunCounts {
	/** The records read from the Feed. */
	int getTotal();

	/** The records that passed validation. */
	int getValid();

	/** The records rejected, each with its reason. */
	int getRejected();

	/** The records new to the Catalogue. */
	int getAdded();

	/** The records that changed a Retailer Product or its Listing. */
	int getChanged();

	/** The records that changed nothing. */
	int getUnchanged();

	/** The active Listings of the Catalogue that a Snapshot did not carry, which it takes off sale. */
	int getMissing();

	/** The Listings taken off sale. */
	int getDeactivated();
}
