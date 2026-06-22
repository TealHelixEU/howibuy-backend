package eu.tealhelix.common.types.validation;

public abstract class AppValidationException extends RuntimeException {
	public AppValidationException() {
	}

	public AppValidationException(String message) {
		super(message);
	}

	public AppValidationException(String message, Throwable cause) {
		super(message, cause);
	}

	public AppValidationException(Throwable cause) {
		super(cause);
	}
}
