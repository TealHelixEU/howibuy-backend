package eu.tealhelix.common.web.authentication.jwt;

import java.util.regex.Pattern;
import jakarta.ws.rs.container.ContainerRequestContext;

/**
 * The bearer token a request carries, as the {@code Authorization} header spells it. Authentication reads it to find out
 * who is calling; an endpoint that acts on the token itself rather than on the user behind it reads it for the token, and
 * both read it the same way.
 */
public interface BearerToken {
	String AUTHORIZATION_HEADER = "Authorization";

	Pattern BEARER = Pattern.compile("^Bearer (.*)$", Pattern.CASE_INSENSITIVE);

	/**
	 * @param requestContext The request
	 * @return The token the request carries, or {@code null} if it carries none
	 */
	static String of(ContainerRequestContext requestContext) {
		var authenticationHeaderValue = requestContext.getHeaders().getFirst(AUTHORIZATION_HEADER);
		if (authenticationHeaderValue == null) {
			return null;
		}
		var bearer = BEARER.matcher(authenticationHeaderValue.trim());
		return bearer.matches() ? bearer.group(1) : null;
	}
}
