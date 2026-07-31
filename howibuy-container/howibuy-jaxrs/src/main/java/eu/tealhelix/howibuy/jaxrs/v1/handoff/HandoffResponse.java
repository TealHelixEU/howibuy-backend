package eu.tealhelix.howibuy.jaxrs.v1.handoff;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HandoffResponse(
		@JsonProperty("ticket")
		String ticket,
		@JsonProperty("expires_in")
		int expiresIn
) {
}
