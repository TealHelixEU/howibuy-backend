package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.JaxRsUtils.currentUser;
import static eu.tealhelix.howibuy.jaxrs.authentication.RequestCatalogue.requireCatalogue;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.PRODUCT_ASSESSMENT;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.PRODUCT_ASSESSMENT_DESC;
import static eu.tealhelix.howibuy.jaxrs.v1.RequestValuesHelper.required;
import static eu.tealhelix.howibuy.jaxrs.v1.RequestValuesHelper.requiredText;

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
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.howibuy.services.v1.ProductAssessmentService;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
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
	/**
	 * The most products a request assesses.
	 */
	static final int MAX_PRODUCTS = 400;

	private static final String FORBIDDEN = "The token is not one of the token exchange, so it names no Catalogue to look the products up in; or a service account presented it, while an assessment belongs to an end-user, whose weighting it is made against.";
	private static final String BAD_PRODUCT = "The request could not be read, or it lacks the product key or the language.";
	private static final String BAD_PRODUCTS = "The request could not be read, it lacks the language or a product key, or it has more than " + MAX_PRODUCTS + " products.";
	private static final String LOOKUP = "The products are looked up by their keys among the products of the Retailer of the Catalogue that the token names, whether or not they are listed in it, and each one is identified by its Classification. ";
	private static final String ALTERNATIVES = "Each answer names up to three alternatives to the product: the one that fits the user's own weighting of the sustainability dimensions, the one that scores best on the scientific weighting, and the best compromise between the two. The outcome's `type` says whether the product could be assessed at all; the alternatives are filled in only when it could.";

	@Inject
	ProductAssessmentService productAssessmentService;

	@Operation(
			summary = "Assess one product",
			description = "Assesses the product named in the body and answers the alternatives to it. " + LOOKUP + ALTERNATIVES)
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
		return Uni.createFrom().item(() -> productKey(request))
				.flatMap(productKey -> productAssessmentService.assessSingleProduct(user, catalogueId, productKey));
	}

	@Operation(
			summary = "Assess several products, answering when all of them are done",
			description = "Assesses every product of the request and answers one outcome per product, in the order sent. A product that cannot be assessed takes a failure outcome of its own and does not bring the rest of the batch down. " + LOOKUP + ALTERNATIVES)
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
		return Uni.createFrom().item(() -> productKeys(request))
				.flatMap(productKeys -> productAssessmentService.assessMultipleProductsSync(user, catalogueId, productKeys))
				.map(MultiProductAssessmentResponse::new);
	}

	@Operation(
			summary = "Assess several products, streaming each outcome as it is ready",
			description = "The same assessment as the batch operation, but each outcome is written out as soon as it is produced, so a client can show the first results without waiting for the last. "
					+ "**The response body is a stream of JSON objects written one after another with no enclosing array and no separators.** The schema below describes one such object, not the body as a whole.")
	@APIResponse(responseCode = "200", description = "The stream of outcomes, one per product, in the order sent.")
	@APIResponse(responseCode = "400", description = BAD_PRODUCTS)
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = FORBIDDEN)
	@POST
	@Path("multi-async")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Multi<ProductAssessmentOutcome> assessMultipleProductsAsync(@Context ContainerRequestContext crc, MultiProductAssessmentRequest request) {
		return RestMulti.fromMultiData(productAssessmentService.assessMultipleProductsAsync(currentUser(crc), requireCatalogue(crc), productKeys(request)))
				.encodeAsJsonArray(false).build();
	}

	/**
	 * @return The key of the product of a request that carries one and a language
	 */
	private static ProductKey productKey(SingleProductAssessmentRequest request) {
		required("body", request);
		requiredText("language", request.language());
		return new ProductKeyImpl(requiredText("productKey", request.productKey()));
	}

	/**
	 * @return The keys of the products of a request that carries a language and at most {@link #MAX_PRODUCTS} of them
	 */
	private static List<ProductKey> productKeys(MultiProductAssessmentRequest request) {
		required("body", request);
		requiredText("language", request.language());
		var productKeys = required("productKeys", request.productKeys());
		if (productKeys.size() > MAX_PRODUCTS) {
			throw BadInputValueException.fromInputNameAndHint("productKeys", "There must be at most " + MAX_PRODUCTS);
		}
		return productKeys.stream().<ProductKey>map(productKey -> new ProductKeyImpl(requiredText("productKeys", productKey))).toList();
	}
}
