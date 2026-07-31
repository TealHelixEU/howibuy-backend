package eu.tealhelix.howibuy.jaxrs.v1.handoff;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The token a handed-over session runs on, answered both when the session starts and every time it slides on.
 */
public record SessionTokenResponse(
		@JsonProperty("access_token")
		String accessToken,
		@JsonProperty("expires_in")
		int expiresIn
) {
}
