package eu.tealhelix.common.web;

public interface CommonOpenApiConstants {
	String UNAUTHENTICATED = "No bearer token was presented, or the one presented cannot be processed.";
	String BAD_REQUEST = "The request could not be read, it was probably malformed.";

	String BEARER_AUTH = "bearerAuth";
}
