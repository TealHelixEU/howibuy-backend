package eu.tealhelix.howibuy.jaxrs.authentication.jwt;

public class TokenGenerationException extends RuntimeException {
	public TokenGenerationException(Throwable cause) {
		super(cause);
	}
}
