package eu.tealhelix.howibuy.jaxrs.v1.handoff;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RedeemResponse(
		@JsonProperty("access_token")
		String accessToken,
		@JsonProperty("expires_in")
		int expiresIn
) {
}
