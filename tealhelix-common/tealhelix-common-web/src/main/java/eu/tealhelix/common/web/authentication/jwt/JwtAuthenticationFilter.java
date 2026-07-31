package eu.tealhelix.common.web.authentication.jwt;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;

import eu.tealhelix.common.web.authentication.JaxRsSecurityContextImpl;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Extract the user info from the request and set the JAX-RS {@code SecurityContext}
 * of this application.
 * <p>
 * <strong>Warning:</strong> the filter is declared with {@code nonBlocking = true} because it performs
 * Hibernate Reactive I/O (via {@link TokenHelper#processToken(String)}), which requires execution on a
 * Vert.x event-loop thread. Without this flag the filter would inherit the threading model of the target
 * resource method, and any blocking resource (e.g., one returning a Qute {@code TemplateInstance}) would
 * cause the reactive session to be opened from a worker thread and fail. For the {@code nonBlocking} setting
 * to take effect, this filter must run before any filter allowed to block; keep this in mind when
 * adding new filters or adjusting priorities.
 */
@SuppressWarnings("unused")
public class JwtAuthenticationFilter {
	private final TokenHelper tokenHelper;

	public JwtAuthenticationFilter(TokenHelper tokenHelper) {
		this.tokenHelper = tokenHelper;
	}

	@ServerRequestFilter(nonBlocking = true)
	public Uni<Void> filter(ContainerRequestContext requestContext) {
		String token = BearerToken.of(requestContext);
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

	private static Response unauthorized() {
		return Response.status(Response.Status.UNAUTHORIZED).build();
	}
}
