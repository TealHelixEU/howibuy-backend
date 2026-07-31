package eu.tealhelix.common.web.authentication.jwt;

import eu.tealhelix.common.v1.model.User;
import io.smallrye.mutiny.Uni;

/**
 * Token processing helper, a facade to ease the job of putting the pieces of JWT authentication together.
 * The idea is that this service is agnostic of the context it runs; it expects an appropriate web component to extract
 * the token from the request <em>somehow</em> and pass it to {@link #processToken(String)} to extract a user.
 */
public interface TokenHelper {
	/**
	 * Process the token to create a {@link User}.
	 *
	 * @param token The token, cannot be {@code null}
	 * @return The user, asynchronously
	 */
	Uni<User> processToken(String token);

	/**
	 * Mint the next token of the handed-over session that the given token belongs to. The token presented has to be one
	 * this application would accept to authenticate a request, so an expired token cannot be renewed: whoever holds one
	 * renews it ahead of its expiry or starts a new session.
	 *
	 * @param token The current token of the session, cannot be {@code null}
	 * @return The next token of the session; a failure with {@code TokenHelperException} if the token presented is not one
	 *         this application would accept, or with {@code NotAuthorizedException} if it is not a token of a handed-over
	 *         session or that session has ended
	 */
	Uni<IssuedToken> renewHandoffToken(String token);

	User makeUnauthenticated();
}
