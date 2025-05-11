package eu.tealhelix.betterme.jaxrs.v1.tokenexchange;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenExchangeResponse(
		@JsonProperty("access_token")
		String accessToken,
		@JsonProperty("expires_in")
		int expiresIn
) {
}
