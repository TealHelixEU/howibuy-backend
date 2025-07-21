package eu.tealhelix.common.web.authentication.jwt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;

import eu.tealhelix.common.web.authentication.JaxRsSecurityContextImpl;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Extract the user info from the request and set the JAX-RS {@code SecurityContext}
 * of this application.
 */
@SuppressWarnings("unused")
public class JwtAuthenticationFilter {
	public static final String AUTHORIZATION_HEADER = "Authorization";
	private static final Pattern AUTH_HEADER_RE = Pattern.compile("^Bearer (.*)$", Pattern.CASE_INSENSITIVE);

	private final TokenHelper tokenHelper;

	public JwtAuthenticationFilter(TokenHelper tokenHelper) {
		this.tokenHelper = tokenHelper;
	}

	@ServerRequestFilter
	public Uni<Void> filter(ContainerRequestContext requestContext) {
		String token = extractRawToken(requestContext);
		if (token != null) {
			return tokenHelper.processToken(token)
					.onItem()
					.invoke(user -> {
						var securityCtx = new JaxRsSecurityContextImpl(user, requestContext.getSecurityContext().isSecure(), "BEARER");
						requestContext.setSecurityContext(securityCtx);
					})
					.replaceWithVoid()
					.onFailure(TokenHelperException.class)
					.transform(e -> new NotAuthorizedException("failed to process token", unauthorized(), e));
		} else {
			var user = tokenHelper.makeUnauthenticated();
			var securityCtx = new JaxRsSecurityContextImpl(user, requestContext.getSecurityContext().isSecure(), null);
			requestContext.setSecurityContext(securityCtx);
			return Uni.createFrom().voidItem();
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
