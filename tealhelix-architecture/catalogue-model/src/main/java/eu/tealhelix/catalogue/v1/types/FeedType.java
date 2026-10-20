package eu.tealhelix.catalogue.v1.types;

/**
 * What a Feed tells about its Catalogue.
 */
public enum FeedType {
	/** The whole Catalogue: a Listing missing from it is no longer on sale. */
	SNAPSHOT,

	/** Some products of the Catalogue, new or changed; nothing missing from it is taken off sale. */
	DELTA,

	/** Products taken off sale in the Catalogue, named only by their product keys. */
	DELETION
}
