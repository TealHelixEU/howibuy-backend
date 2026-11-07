package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.JaxRsUtils.currentUser;
import static eu.tealhelix.common.web.RequestValuesHelper.nullable;
import static eu.tealhelix.common.web.RequestValuesHelper.required;
import static eu.tealhelix.howibuy.jaxrs.authentication.RequestCatalogue.requireCatalogue;
import static eu.tealhelix.howibuy.services.v1.ProductAssessmentService.MAX_PRODUCTS;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.PRODUCT_ASSESSMENT;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.PRODUCT_ASSESSMENT_DESC;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.howibuy.services.v1.ProductAssessmentService;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("assessment/product")
@Tag(name = PRODUCT_ASSESSMENT, description = PRODUCT_ASSESSMENT_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class ProductAssessmentResource {
	private static final String FORBIDDEN = "The token is not one of the token exchange, so it names no Catalogue to look the products up in; or a service account presented it, while an assessment belongs to an end-user, whose weighting it is made against.";
	private static final String BAD_PRODUCT = "The request could not be read, or it lacks the product key or the language.";
	private static final String BAD_PRODUCTS = "The request could not be read, it lacks the language or a product key, or it has more than " + MAX_PRODUCTS + " products.";
	private static final String LOOKUP = "The products are looked up by their keys among the products of the Retailer of the Catalogue that the token names, whether or not they are listed in it, and each one is identified by its Classification. ";
	private static final String RECOMMENDATION = "Each answer says whether there is something better to buy in place of the product and, when there is, names up to three alternatives, one for each degree of substitutability at which the Catalogue stocks something better, the closest first: each a product of the Catalogue that may stand in for it and scores higher on the scientific weighting of the sustainability dimensions, picked at random in favour of the user's own weighting, and the same each time the user assesses the same product. The outcome's `type` says whether the product could be assessed at all; the recommendation and the alternatives are filled in only when it could.";

	@Inject
	ProductAssessmentService productAssessmentService;

	@Operation(
			summary = "Assess one product",
			description = "Assesses the product named in the body and answers whether there is something better to buy in its place. " + LOOKUP + RECOMMENDATION)
	@APIResponse(responseCode = "200", description = "The product was put through the assessment. A product the assessment could not place is reported by the outcome's `type`, not by an error status.")
	@APIResponse(responseCode = "400", description = BAD_PRODUCT)
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = FORBIDDEN)
	@POST
	@Path("single")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<ProductAssessmentOutcome> assessSingleProduct(@Context ContainerRequestContext crc, SingleProductAssessmentRequest request) {
		var user = currentUser(crc);
		var catalogueId = requireCatalogue(crc);
		return Uni.createFrom().item(() -> required("body", request))
				.flatMap(body -> productAssessmentService.assessSingleProduct(user, catalogueId, nullable(body.productKey(), ProductKeyImpl::new), body.language()));
	}

	@Operation(
			summary = "Assess several products, answering when all of them are done",
			description = "Assesses every product of the request and answers one outcome per product, in the order sent. A product that cannot be assessed takes a failure outcome of its own and does not bring the rest of the batch down. " + LOOKUP + RECOMMENDATION)
	@APIResponse(responseCode = "200", description = "Every product of the request was put through the assessment.")
	@APIResponse(responseCode = "400", description = BAD_PRODUCTS)
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = FORBIDDEN)
	@POST
	@Path("multi")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<MultiProductAssessmentResponse> assessMultipleProductsSync(@Context ContainerRequestContext crc, MultiProductAssessmentRequest request) {
		var user = currentUser(crc);
		var catalogueId = requireCatalogue(crc);
		return Uni.createFrom().item(() -> required("body", request))
				.flatMap(body -> productAssessmentService.assessMultipleProductsSync(user, catalogueId, AssessmentRequests.productKeys(body.productKeys()), body.language()))
				.map(MultiProductAssessmentResponse::new);
	}
}
