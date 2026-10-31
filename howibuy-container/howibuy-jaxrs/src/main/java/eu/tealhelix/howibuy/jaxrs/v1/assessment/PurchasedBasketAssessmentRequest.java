package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import java.time.OffsetDateTime;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A basket a user bought, as the Retailer registers it: the user, named the way the Retailer knows them, the Catalogue they bought it from and its products.")
public record PurchasedBasketAssessmentRequest(
		@Schema(description = "The Retailer's own id for the user, at most 100 characters. One this application has not seen before brings the user into being.")
		String correlationId,
		@Schema(description = "The id of the Retailer's active Catalogue the basket was bought from.")
		String catalogueId,
		@Schema(description = "When the user bought the basket, in ISO-8601 with an offset. Missing for the time of the request. It may not be in the future, though a clock running a few minutes ahead of this application's is tolerated.")
		OffsetDateTime purchasedAt,
		@Schema(description = "ISO 639-1 code of the language to answer in.")
		String language,
		@Schema(description = "The retailer's keys of the products in the basket, at most " + ProductAssessmentResource.MAX_PRODUCTS + ".")
		List<String> productKeys
) implements BasketRequest {
}
