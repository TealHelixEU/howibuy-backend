package eu.tealhelix.howibuy.jaxrs.v1.handoff;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The ticket to send a user over to the single-page application with.\n\nTo use it, create a URL for the SPA and append `#ticket=<the_ticket_field>` to it.")
public record HandoffResponse(
		@JsonProperty("ticket")
		@Schema(description = "The ticket, redeemable once. This application will not produce it a second time.")
		String ticket,
		@JsonProperty("expires_in")
		@Schema(description = "Seconds the ticket stays redeemable.")
		int expiresIn
) {
}
