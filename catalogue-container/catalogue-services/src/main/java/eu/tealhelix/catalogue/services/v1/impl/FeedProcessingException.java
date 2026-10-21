package eu.tealhelix.catalogue.services.v1.impl;

/**
 * A Feed cannot be processed, for a reason its Ingestion Run records as its error.
 */
public class FeedProcessingException extends RuntimeException {
	public FeedProcessingException(String message) {
		super(message);
	}
}
