package eu.tealhelix.catalogue.jaxrs.authentication;

import java.util.Set;
import jakarta.ws.rs.core.SecurityContext;

import eu.tealhelix.common.types.impl.EmailAddressImpl;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.common.web.authentication.IdmRealmRoles;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * The caller of a request, as the access token it presented tells. The token was verified before the request reached
 * the resources; a request without a token is made by the unauthenticated user.
 */
public final class CurrentUser {
	/**
	 * The claim naming the client the IDM issued a token to, when the client acts on its own behalf; a token issued for a
	 * person leaves it out.
	 */
	private static final String CLIENT_ID_CLAIM = "client_id";

	private static final String USERNAME_CLAIM = "preferred_username";

	private static final String EMAIL_CLAIM = "email";

	private CurrentUser() {
	}

	/**
	 * The caller of a request. Its id is the subject of the token, which is the id of the Retailer for a Retailer's
	 * client, and its roles come from the realm roles of the token.
	 *
	 * @param securityContext The security context of the request
	 * @return The caller, the unauthenticated user if the request carried no token
	 */
	public static User of(SecurityContext securityContext) {
		if (!(securityContext.getUserPrincipal() instanceof JsonWebToken token) || token.getSubject() == null) {
			return new UserImpl(null, null, null, false, true, Set.of());
		} else {
			String clientId = token.getClaim(CLIENT_ID_CLAIM);
			String username = token.getClaim(USERNAME_CLAIM);
			String email = token.getClaim(EMAIL_CLAIM);
			return new UserImpl(
					new UserIdImpl(token.getSubject()),
					clientId != null ? clientId : username,
					email != null ? new EmailAddressImpl(email) : null,
					false,
					clientId == null,
					IdmRealmRoles.rolesOf(securityContext::isUserInRole)
			);
		}
	}
}
