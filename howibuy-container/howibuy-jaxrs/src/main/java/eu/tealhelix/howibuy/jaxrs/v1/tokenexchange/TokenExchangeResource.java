package eu.tealhelix.howibuy.jaxrs.v1.tokenexchange;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import eu.tealhelix.howibuy.services.v1.UserImpersonationService;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.web.authentication.jwt.JwtGenerationService;
import io.smallrye.mutiny.Uni;

@Path("tokenexchange")
public class TokenExchangeResource {
	@Inject
	UserImpersonationService userImpersonationService;

	@Inject
	JwtGenerationService jwtGenerationService;

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Response> exchangeToken(@Context ContainerRequestContext crc, TokenExchangeRequest request) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return userImpersonationService.impersonateUserAsRetailer(user, request.correlationId())
				.map(impersonatedUser -> jwtGenerationService.toTokenForImpersonation(impersonatedUser))
				.map(t -> new TokenExchangeResponse(t.accessToken(), t.expiresInSeconds()))
				.map(t -> Response.ok(t).build());
	}
}
