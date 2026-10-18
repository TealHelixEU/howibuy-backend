package eu.tealhelix.common.v1.model;

/**
 * What a caller may do across the services of TealHelix, whether the caller is a person or a service account.
 */
public enum Role {
	/** A retailer, which pushes its feeds and acts for the users it sends over. */
	RETAILER,

	/** The Catalogue Sync, which carries the catalogue changes from the Ingestion to HowiBuy. */
	CATALOGUE_SYNC,

	/** An operator, who looks after the ingestion of the feeds. */
	OPERATOR
}
