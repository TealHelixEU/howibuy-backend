package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BAD_REQUEST;
import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.JaxRsUtils.currentUser;
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

import eu.tealhelix.howibuy.services.v1.ProductAssessmentService;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductData;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestMulti;

@Path("assessment")
@Tag(name = PRODUCT_ASSESSMENT, description = PRODUCT_ASSESSMENT_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class ProductAssessmentResource {
	private static final String SERVICE_ACCOUNT = "A service account presented the token: an assessment belongs to an end-user, whose weighting it is made against.";
	private static final String ALTERNATIVES = "Each answer names up to three alternatives to the product: the one that fits the user's own weighting of the sustainability dimensions, the one that scores best on the scientific weighting, and the best compromise between the two. The outcome's `type` says whether the product could be assessed at all; the alternatives are filled in only when it could.";

	@Inject
	ProductAssessmentService productAssessmentService;

	@Operation(
			summary = "Assess one product",
			description = "Assesses the product sent in the body and answers the alternatives to it. " + ALTERNATIVES)
	@APIResponse(responseCode = "200", description = "The product was put through the assessment. A product the assessment could not place is reported by the outcome's `type`, not by an error status.")
	@APIResponse(responseCode = "400", description = BAD_REQUEST)
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = SERVICE_ACCOUNT)
	@POST
	@Path("single")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<ProductAssessmentOutcome> assessSingleProduct(@Context ContainerRequestContext crc, ProductData productData) {
		return productAssessmentService.assessSingleProduct(currentUser(crc), productData);
	}

	@Operation(
			summary = "Assess several products, answering when all of them are done",
			description = "Assesses every product of the request and answers one outcome per product, in the order sent. A product that cannot be assessed takes a failure outcome of its own and does not bring the rest of the batch down. " + ALTERNATIVES)
	@APIResponse(responseCode = "200", description = "Every product of the request was put through the assessment.")
	@APIResponse(responseCode = "400", description = BAD_REQUEST)
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = SERVICE_ACCOUNT)
	@POST
	@Path("multi")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<MultiProductAssessmentResponse> assessMultipleProductsSync(@Context ContainerRequestContext crc, MultiProductAssessmentRequest request) {
		return productAssessmentService.assessMultipleProductsSync(currentUser(crc), request.products())
				.map(MultiProductAssessmentResponse::new);
	}

	@Operation(
			summary = "Assess several products, streaming each outcome as it is ready",
			description = "The same assessment as the batch operation, but each outcome is written out as soon as it is produced, so a client can show the first results without waiting for the last. "
					+ "**The response body is a stream of JSON objects written one after another with no enclosing array and no separators.** The schema below describes one such object, not the body as a whole.")
	@APIResponse(responseCode = "200", description = "The stream of outcomes, one per product, in the order sent.")
	@APIResponse(responseCode = "400", description = BAD_REQUEST)
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = SERVICE_ACCOUNT)
	@POST
	@Path("multi-async")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Multi<ProductAssessmentOutcome> assessMultipleProductsAsync(@Context ContainerRequestContext crc, MultiProductAssessmentRequest request) {
		return RestMulti.fromMultiData(productAssessmentService.assessMultipleProductsAsync(currentUser(crc), request.products()))
				.encodeAsJsonArray(false).build();
	}
}
