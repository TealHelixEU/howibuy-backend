package eu.tealhelix.common.types.authorization;

/**
 * Generic superclass of security-related exceptions in this application.
 */
public abstract class AppSecurityException extends RuntimeException {
	/**
	 * Default constructor.
	 */
	public AppSecurityException() {
		super();
	}

	/**
	 * Construct an exception with a message.
	 * 
	 * @param message The message
	 */
	public AppSecurityException(String message) {
		super(message);
	}

	/**
	 * Construct an exception with a cause.
	 * 
	 * @param cause The cause
	 */
	public AppSecurityException(Throwable cause) {
		super(cause);
	}

	/**
	 * Construct an exception with a message and a cause.
	 * 
	 * @param message The message
	 * @param cause   The cause
	 */
	public AppSecurityException(String message, Throwable cause) {
		super(message, cause);
	}
}
