package eu.tealhelix.howibuy.jaxrs.v1.handoff;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The ticket being redeemed. It is the only credential this request carries.")
public record RedeemRequest(
		@Schema(description = "The ticket, as it was handed to the retailer that sent the user over.")
		String ticket
) {
}
