package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import eu.tealhelix.howibuy.services.v1.ProductAssessmentService;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A basket a user bought, as the Retailer registers it: the user, named the way the Retailer knows them, the Catalogue they bought it from and its products.")
public record PurchasedBasketAssessmentRequest(
		@Schema(description = "The Retailer's own id for the user, at most 100 characters. One this application has not seen before brings the user into being.")
		String correlationId,
		@Schema(description = "The id of the Retailer's active Catalogue the basket was bought from.")
		String catalogueId,
		@Schema(description = "When the user bought the basket: the date and time at the shop, in ISO-8601 with the shop's offset from UTC, e.g. `2026-11-06T18:30:00+02:00`. The offset decides the month the purchase counts in. It may not be in the future, though a clock running a few minutes ahead of this application's is tolerated.")
		@JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
		OffsetDateTime purchasedAt,
		@Schema(description = "ISO 639-1 code of the language to answer in.")
		String language,
		@Schema(description = "The retailer's keys of the products in the basket, at most " + ProductAssessmentService.MAX_PRODUCTS + ".")
		List<String> productKeys
) implements BasketRequest {
}
