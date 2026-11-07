package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.JaxRsUtils.currentUser;
import static eu.tealhelix.common.utils.UniComprehensions.forc;
import static eu.tealhelix.common.web.RequestValuesHelper.required;
import static eu.tealhelix.common.web.RequestValuesHelper.requiredText;
import static eu.tealhelix.howibuy.jaxrs.authentication.RequestCatalogue.requireCatalogue;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.BASKET_ASSESSMENT;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.BASKET_ASSESSMENT_DESC;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.howibuy.services.v1.BasketAssessmentService;
import eu.tealhelix.howibuy.services.v1.UserImpersonationService;
import eu.tealhelix.howibuy.v1.model.BasketAssessmentOutcome;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("assessment/basket")
@Tag(name = BASKET_ASSESSMENT, description = BASKET_ASSESSMENT_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class BasketAssessmentResource {
	private static final String FORBIDDEN = "The token is not one of the token exchange, so it names no Catalogue to look the products up in; or a service account presented it, while an assessment belongs to an end-user, whose weighting it is made against.";
	private static final String BAD_BASKET = "The request could not be read, it lacks the language or a product key, or it has more than " + ProductAssessmentResource.MAX_PRODUCTS + " products.";

	private static final String PURCHASE_FORBIDDEN = "The caller is not an active retailer of this application, the Catalogue named is not an active Catalogue of the retailer, or the user named has not consented to it.";
	private static final String BAD_PURCHASE = "The request could not be read, it lacks the Catalogue id, the purchase time, the language or a product key, its correlation id is missing or longer than 100 characters, its purchase time has no offset or is in the future, or it has more than " + ProductAssessmentResource.MAX_PRODUCTS + " products.";

	@Inject
	BasketAssessmentService basketAssessmentService;

	@Inject
	UserImpersonationService userImpersonationService;

	@Operation(
			summary = "Assess a basket before the purchase",
			description = "Judges the basket named in the body as a whole: how well it matches the user's own priorities, the products that keep it from being sustainable, lowest score first, the experience points the basket would earn, what it would do to the user's streak and the badges it would earn. "
					+ "The products are looked up by their keys among the products of the Retailer of the Catalogue that the token names, and each one is identified by its Classification. "
					+ "A product sent more than once counts once. A product that cannot be judged (unknown, not classified, matching no archetype, or with no Nutri-Score) is left out and does not count against the user. "
					+ "Nothing is stored: the user's experience points total and streak are answered as they stand, and a badge the basket would earn is not given. "
					+ "The outcome's `type` says whether any product of the basket could be judged; the match and its label are filled in only when one could.")
	@APIResponse(responseCode = "200", description = "The basket was put through the assessment. A basket with no product that can be judged is reported by the outcome's `type`, not by an error status.")
	@APIResponse(responseCode = "400", description = BAD_BASKET)
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = FORBIDDEN)
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<BasketAssessmentOutcome> assessBasketPrePurchase(@Context ContainerRequestContext crc, BasketAssessmentRequest request) {
		var user = currentUser(crc);
		var catalogueId = requireCatalogue(crc);
		return Uni.createFrom().item(() -> productKeys(request))
				.flatMap(productKeys -> basketAssessmentService.assessBasketPrePurchase(user, catalogueId, productKeys, request.language()));
	}

	/**
	 * Registers the purchase of a basket from the Retailer's backend, with the Retailer's own token. There is no
	 * idempotency: every call is another purchase, so it must be made exactly once per purchase.
	 */
	@Operation(
			summary = "Register a purchased basket",
			description = "Assesses the basket a user bought, as the basket before the purchase is assessed, stores it with the scores of its products and the badges it earns, adds the experience points it earns to the user's total and applies its effect to the user's streak; the answer includes both. "
					+ "The Retailer calls it from its backend, with its own token, and names the user by its correlation id; one this application has not seen before brings the user into being, consented to the Retailer. "
					+ "**Call it exactly once per purchase:** there is no idempotency, so a repeated call stores the basket again, adds its experience points again, applies its effect to the streak again and counts its products towards the badges again. "
					+ "A basket with no product that can be judged is stored too, earning nothing and leaving the streak as it is.")
	@APIResponse(responseCode = "200", description = "The purchase was registered. A basket with no product that can be judged is reported by the outcome's `type`, not by an error status.")
	@APIResponse(responseCode = "400", description = BAD_PURCHASE)
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = PURCHASE_FORBIDDEN)
	@POST
	@Path("purchase")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<BasketAssessmentOutcome> assessPurchasedBasket(@Context ContainerRequestContext crc, PurchasedBasketAssessmentRequest request) {
		var retailer = currentUser(crc);
		return Uni.createFrom().item(() -> productKeys(request))
				.flatMap(productKeys -> {
					var catalogueId = new CatalogueIdImpl(requiredText("catalogueId", request.catalogueId()));
					var purchasedAt = required("purchasedAt", request.purchasedAt());
					return forc(
							userImpersonationService.impersonateUserAsRetailer(retailer, request.correlationId(), catalogueId),
							user -> basketAssessmentService.assessPurchasedBasket(user, catalogueId, purchasedAt, productKeys, request.language()));
				});
	}

	/**
	 * @return The keys of the products of a request that carries a language and at most
	 *         {@link ProductAssessmentResource#MAX_PRODUCTS} of them
	 */
	private static List<ProductKey> productKeys(BasketRequest request) {
		required("body", request);
		return AssessmentRequests.productKeys(request.language(), request.productKeys());
	}
}
