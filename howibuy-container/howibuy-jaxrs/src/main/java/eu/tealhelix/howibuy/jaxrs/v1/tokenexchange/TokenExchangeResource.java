package eu.tealhelix.howibuy.jaxrs.v1.tokenexchange;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.TOKEN_EXCHANGE;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.TOKEN_EXCHANGE_DESC;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.web.authentication.jwt.JwtGenerationService;
import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import eu.tealhelix.howibuy.services.v1.UserImpersonationService;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("tokenexchange")
@Tag(name = TOKEN_EXCHANGE, description = TOKEN_EXCHANGE_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class TokenExchangeResource {
	@Inject
	UserImpersonationService userImpersonationService;

	@Inject
	JwtGenerationService jwtGenerationService;

	@Operation(
			summary = "Exchange a retailer's token for one that acts as one of its users",
			description = "The retailer names the user by its own correlation id and is answered a token this application signed, which acts as that user until it expires. A correlation id this application has not seen before brings the user into being, consented to the retailer that named them.")
	@APIResponse(responseCode = "200", description = "The token to act as the named user with, and the seconds it is good for.")
	@APIResponse(responseCode = "400", description = "Malformed request, the correlation id is missing or longer than 100 characters.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = "The caller is not an active retailer of this application, or the user named has not consented to it.")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<TokenExchangeResponse> exchangeToken(@Context ContainerRequestContext crc, TokenExchangeRequest request) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return userImpersonationService.impersonateUserAsRetailer(user, request.correlationId())
				.map(impersonatedUser -> jwtGenerationService.toTokenForImpersonation(impersonatedUser.getId()))
				.map(t -> new TokenExchangeResponse(t.accessToken(), t.expiresInSeconds()));
	}
}
