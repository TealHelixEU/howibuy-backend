package eu.tealhelix.howibuy.jaxrs.v1.handoff;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.web.authentication.jwt.JwtGenerationService;
import eu.tealhelix.howibuy.services.v1.HandoffService;
import eu.tealhelix.howibuy.services.v1.UserImpersonationService;
import io.smallrye.mutiny.Uni;

@Path("handoff")
public class HandoffResource {
	@Inject
	UserImpersonationService userImpersonationService;

	@Inject
	HandoffService handoffService;

	@Inject
	JwtGenerationService jwtGenerationService;

	/**
	 * Hand a retailer a ticket for one of its users, to send that user over to the single-page application with. The
	 * retailer identifies the user the same way as for the token exchange, and is answered a ticket instead of a token
	 * because what follows happens in the user's browser: a ticket is worth nothing until redeemed, and nothing again
	 * afterwards.
	 */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Response> mintTicket(@Context ContainerRequestContext crc, HandoffRequest request) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return userImpersonationService.impersonateUserAsRetailer(user, request.correlationId())
				.flatMap(handoffService::mintTicket)
				.map(t -> new HandoffResponse(t.ticket(), t.expiresInSeconds()))
				.map(t -> Response.ok(t).build());
	}

	/**
	 * Redeem a ticket for the first token of the session it opens. This is the one operation of the application that asks
	 * for no token of its own, because the single-page application holds none yet: the ticket is the credential, which is
	 * why it is worth so little for so short a time. Nothing here reads the security context, so nothing here can be
	 * reached by presenting anything other than the ticket.
	 */
	@POST
	@Path("redeem")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Response> redeemTicket(RedeemRequest request) {
		return handoffService.redeemTicket(request.ticket())
				.map(jwtGenerationService::toTokenForHandoff)
				.map(t -> new RedeemResponse(t.accessToken(), t.expiresInSeconds()))
				.map(t -> Response.ok(t).build());
	}
}
