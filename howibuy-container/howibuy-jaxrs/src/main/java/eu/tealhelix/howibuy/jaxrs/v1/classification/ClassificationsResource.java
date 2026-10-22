package eu.tealhelix.howibuy.jaxrs.v1.classification;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.JaxRsUtils.currentUser;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.CLASSIFICATION;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.CLASSIFICATION_DESC;
import static eu.tealhelix.howibuy.jaxrs.v1.RequestValuesHelper.required;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import eu.tealhelix.howibuy.services.v1.ClassificationService;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("classifications")
@Tag(name = CLASSIFICATION, description = CLASSIFICATION_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class ClassificationsResource {
	static final String FORBIDDEN = "The caller is neither the Catalogue Sync nor an operator.";

	@Inject
	ClassificationService classificationService;

	@Operation(
			summary = "Read the products waiting to be classified",
			description = "The products without a Classification, and those changed since theirs was resolved, the one changed longest ago first. An attempt at one is recorded with the input hash read here.")
	@APIResponse(responseCode = "200", description = "The pending products, as many as the limit at most.")
	@APIResponse(responseCode = "400", description = "The limit is missing or out of its range.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = FORBIDDEN)
	@GET
	@Path("pending")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<PendingProductDto>> findPending(
			@Context ContainerRequestContext crc,
			@Parameter(description = "How many products to read at most, from 1 to " + ClassificationService.MAX_PENDING_PER_PAGE + ".", required = true) @QueryParam("limit") Integer limit
	) {
		var user = currentUser(crc);
		return Uni.createFrom().item(() -> required("limit", limit))
				.flatMap(checkedLimit -> classificationService.findPending(user, checkedLimit))
				.map(products -> products.stream().map(ClassificationsResource::toDto).toList());
	}

	private static PendingProductDto toDto(PendingProduct product) {
		return new PendingProductDto(product.getId().asString(), product.getInputHash(), product.getLanguages());
	}
}
