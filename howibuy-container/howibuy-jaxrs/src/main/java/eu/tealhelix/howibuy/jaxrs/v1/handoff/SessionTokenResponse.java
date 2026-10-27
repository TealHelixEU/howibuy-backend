package eu.tealhelix.howibuy.jaxrs.v1.handoff;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * The token a handed-over session runs on, answered both when the session starts and every time it slides on.
 */
@Schema(description = "The token a handed-over session runs on, answered when the session starts and again on every renewal.")
public record SessionTokenResponse(
		@JsonProperty("access_token")
		@Schema(description = "The bearer token to present on the requests that follow.")
		String accessToken,
		@JsonProperty("expires_in")
		@Schema(description = "Seconds the token is good for: the handoff session time, or less when the session ends sooner. The client renews before the token expires; a session whose token has died is over.")
		int expiresIn
) {
}
