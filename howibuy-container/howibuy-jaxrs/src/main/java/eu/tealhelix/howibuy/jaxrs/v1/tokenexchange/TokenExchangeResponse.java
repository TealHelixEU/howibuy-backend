package eu.tealhelix.howibuy.jaxrs.v1.tokenexchange;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The token that acts as the named user, for a retailer calling on that user's behalf.")
public record TokenExchangeResponse(
		@JsonProperty("access_token")
		@Schema(description = "The bearer token, signed by this application, to present on the requests made as the user.")
		String accessToken,
		@JsonProperty("expires_in")
		@Schema(description = "Seconds the token is good for.")
		int expiresIn
) {
}
