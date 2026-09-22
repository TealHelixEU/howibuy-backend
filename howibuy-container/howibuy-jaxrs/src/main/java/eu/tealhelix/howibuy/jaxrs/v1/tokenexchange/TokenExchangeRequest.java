package eu.tealhelix.howibuy.jaxrs.v1.tokenexchange;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The user to act as, named the way the retailer knows them.")
public record TokenExchangeRequest(
		@Schema(description = "The retailer's own id for the user, at most 100 characters. One this application has not seen before brings the user into being.")
		String correlationId
) {
}
