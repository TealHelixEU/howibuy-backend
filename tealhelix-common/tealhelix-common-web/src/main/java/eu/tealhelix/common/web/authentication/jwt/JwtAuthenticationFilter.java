package eu.tealhelix.common.web.authentication.jwt;

import static jakarta.ws.rs.Priorities.AUTHENTICATION;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.tealhelix.common.web.authentication.JaxRsSecurityContextImpl;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Extract the user info from the request and set the JAX-RS {@code SecurityContext}
 * of this application.
 */
@Provider
@Priority(AUTHENTICATION)
public class JwtAuthenticationFilter implements ContainerRequestFilter {
	public static final String AUTHORIZATION_HEADER = "Authorization";
	private static final Pattern AUTH_HEADER_RE = Pattern.compile("^Bearer (.*)$", Pattern.CASE_INSENSITIVE);

	@Inject
	private TokenHelper tokenHelper;

	@Override
	public void filter(ContainerRequestContext requestContext) {
		String token = extractRawToken(requestContext);
		if (token != null) {
			try {
				var user = tokenHelper.processToken(token);
				var securityCtx = new JaxRsSecurityContextImpl(user, requestContext.getSecurityContext().isSecure(), "BEARER");
				requestContext.setSecurityContext(securityCtx);
			} catch (TokenHelperException e) {
				throw new NotAuthorizedException("failed to process token", unauthorized(), e);
			}
		} else {
			var user = tokenHelper.makeUnauthenticated();
			var securityCtx = new JaxRsSecurityContextImpl(user, requestContext.getSecurityContext().isSecure(), null);
			requestContext.setSecurityContext(securityCtx);
		}
	}

	private String extractRawToken(ContainerRequestContext requestContext) {
		String authenticationHeaderValue = requestContext.getHeaders().getFirst(AUTHORIZATION_HEADER);
		String result = null;
		if (authenticationHeaderValue != null) {
			Matcher m = AUTH_HEADER_RE.matcher(authenticationHeaderValue.trim());
			if (m.matches()) {
				result = m.group(1);
			}
		}
		return result;
	}

	private static Response unauthorized() {
		return Response.status(Response.Status.UNAUTHORIZED).build();
	}
}
