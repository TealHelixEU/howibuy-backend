package eu.tealhelix.catalogue.jaxrs.v1.productchanges;

import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.PRODUCT_CHANGES;
import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.PRODUCT_CHANGES_DESC;
import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

import eu.tealhelix.catalogue.jaxrs.authentication.CurrentUser;
import eu.tealhelix.catalogue.services.v1.ProductChangeService;
import eu.tealhelix.catalogue.services.v1.types.ProductChangePage;
import eu.tealhelix.catalogue.v1.model.Listing;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("product-changes")
@Tag(name = PRODUCT_CHANGES, description = PRODUCT_CHANGES_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class ProductChangesResource {
	@Inject
	ProductChangeService productChangeService;

	@Operation(summary = "Read the Retailer Products that changed after a value of the change sequence",
			description = "A page of them, the lowest change sequence first, each in its complete current state, once, at the latest value it was stamped with. The next page is read after the page's toSequence; the page after the last change is empty.")
	@APIResponse(responseCode = "200", description = "The page.")
	@APIResponse(responseCode = "400", description = "The value to read after is negative, or the limit is out of range.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = "The caller is not the Catalogue Sync.")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<ProductChangePageDto> find(
			@Context SecurityContext securityContext,
			@Parameter(description = "The value of the change sequence to read after, 0 if left out to read from the first change.") @QueryParam("after") Long after,
			@Parameter(description = "How many products to read at most, from 1 to a configured maximum; a configured default if left out.") @QueryParam("limit") Integer limit
	) {
		var user = CurrentUser.of(securityContext);
		return productChangeService.findChanges(user, after != null ? after : 0, limit).map(ProductChangesResource::toDto);
	}

	private static ProductChangePageDto toDto(ProductChangePage page) {
		return new ProductChangePageDto(page.toSequence(), page.products().stream().map(ProductChangesResource::toDto).toList());
	}

	private static ProductChangeDto toDto(ProductChange product) {
		if (product.isRemoved()) {
			return new ProductChangeDto(product.getRetailerId().asString(), product.getProductKey().asString(), product.getChangeSequence(), true, null, null, null, null, null);
		}
		return new ProductChangeDto(
				product.getRetailerId().asString(),
				product.getProductKey().asString(),
				product.getChangeSequence(),
				false,
				product.getInputHash(),
				product.getNames(),
				product.getCharacteristics(),
				product.getTags(),
				product.getListings().stream().map(ProductChangesResource::toDto).toList()
		);
	}

	private static ListingDto toDto(Listing listing) {
		return new ListingDto(listing.getCatalogueId().asString(), listing.getPrice(), listing.getCurrency().getCurrencyCode(), listing.isActive());
	}
}
