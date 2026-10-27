package eu.tealhelix.howibuy.jaxrs.v1.tokenexchange;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The user to act as, named the way the retailer knows them, and the Catalogue to act in.")
public record TokenExchangeRequest(
		@Schema(description = "The retailer's own id for the user, at most 100 characters. One this application has not seen before brings the user into being.")
		String correlationId,
		@Schema(description = "The id of the retailer's active Catalogue whose products are assessed for the user.")
		String catalogueId
) {
}
