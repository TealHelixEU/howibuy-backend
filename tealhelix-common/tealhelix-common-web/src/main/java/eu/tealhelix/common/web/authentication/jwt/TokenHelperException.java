package eu.tealhelix.common.web.authentication.jwt;

/**
 * Indicate that something went wrong with the authentication, the sign-in should fail and the caller should respond
 * with an appropriate HTTP code (401). The message exists for logging reasons, it should not be part of the response.
 */
public class TokenHelperException extends RuntimeException {
	public TokenHelperException() {
	}

	public TokenHelperException(String message) {
		super(message);
	}

	public TokenHelperException(Throwable cause) {
		super(cause);
	}

	public TokenHelperException(String message, Throwable cause) {
		super(message, cause);
	}
}
