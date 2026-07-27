package eu.tealhelix.sfc.services.v1.types;

/**
 * Thrown when an action that operates on the user's in-progress attempt — completing it — is requested but the user
 * has no attempt in progress (mapped to HTTP 409).
 */
public class NoInProgressAttemptException extends RuntimeException {
	public NoInProgressAttemptException() {
		super("There is no compass attempt in progress.");
	}
}
