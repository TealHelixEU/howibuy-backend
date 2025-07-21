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

	User makeUnauthenticated();
}
