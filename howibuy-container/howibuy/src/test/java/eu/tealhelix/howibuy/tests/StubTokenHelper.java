package eu.tealhelix.howibuy.tests;

import java.util.Map;
import java.util.Set;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.howibuy.jaxrs.authentication.jwt.Authentication;
import eu.tealhelix.howibuy.jaxrs.authentication.jwt.TokenHelperImpl;
import io.smallrye.mutiny.Uni;

/**
 * Fakes the decoding of the JWT: a token is a plain name, authenticated as given.
 */
final class StubTokenHelper extends TokenHelperImpl {
	private final Map<String, Authentication> authenticationsByToken;

	StubTokenHelper(Map<String, Authentication> authenticationsByToken) {
		super(null, null, null, null, null);
		this.authenticationsByToken = authenticationsByToken;
	}

	@Override
	public Uni<Authentication> processToken(String token) {
		return Uni.createFrom().item(authenticationsByToken.get(token));
	}

	@Override
	public User makeUnauthenticated() {
		return new UserImpl(null, null, null, false, true, Set.of());
	}
}
