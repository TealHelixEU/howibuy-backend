package eu.tealhelix.howibuy.jaxrs.v1.handoff;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The user to hand over, named the way the retailer knows them i.e., the correlation id.")
public record HandoffRequest(
		@Schema(description = "The retailer's own id for the user, at most 100 characters. One this application has not seen before brings the user into being.")
		String correlationId
) {
}
