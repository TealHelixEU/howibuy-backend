package eu.tealhelix.common.types.authorization;

/**
 * No authentication information could be retrieved or authentication failed.
 */
public class NotAuthenticatedException extends AppSecurityException {
	/**
	 * Default constructor.
	 */
	public NotAuthenticatedException() {
		// NOOP
	}

	/**
	 * @param message The message
	 */
	public NotAuthenticatedException(String message) {
		super(message);
	}

	/**
	 * @param cause The cause
	 */
	public NotAuthenticatedException(Throwable cause) {
		super(cause);
	}

	/**
	 * @param message The message
	 * @param cause   The cause
	 */
	public NotAuthenticatedException(String message, Throwable cause) {
		super(message, cause);
	}
}
