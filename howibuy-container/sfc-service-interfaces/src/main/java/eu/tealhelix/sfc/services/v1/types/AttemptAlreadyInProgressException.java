package eu.tealhelix.sfc.services.v1.types;

/**
 * Thrown when a fresh attempt is asked for while the user already has one in progress. At most one attempt is in
 * progress at a time, so the answers on it are never ambiguous; the one already open is the one to carry on with
 * (mapped to HTTP 409).
 */
public class AttemptAlreadyInProgressException extends RuntimeException {
	public AttemptAlreadyInProgressException() {
		super("A compass attempt is already in progress.");
	}
}
