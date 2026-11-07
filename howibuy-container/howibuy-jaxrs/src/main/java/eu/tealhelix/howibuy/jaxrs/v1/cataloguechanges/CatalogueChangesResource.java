package eu.tealhelix.howibuy.jaxrs.v1.cataloguechanges;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.JaxRsUtils.currentUser;
import static eu.tealhelix.common.web.RequestValuesHelper.built;
import static eu.tealhelix.common.web.RequestValuesHelper.required;
import static eu.tealhelix.common.web.RequestValuesHelper.requiredText;
import static eu.tealhelix.common.web.RequestValuesHelper.uuid;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.CATALOGUE_CHANGES;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.CATALOGUE_CHANGES_DESC;

import java.util.Currency;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.catalogue.v1.model.ImmutableListing;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.Listing;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import eu.tealhelix.howibuy.services.v1.CatalogueChangeService;
import eu.tealhelix.howibuy.services.v1.types.ProductChangePage;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("catalogue-changes")
@Tag(name = CATALOGUE_CHANGES, description = CATALOGUE_CHANGES_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class CatalogueChangesResource {
	private static final String FORBIDDEN = "The caller is not the Catalogue Sync.";

	@Inject
	CatalogueChangeService catalogueChangeService;

	@Operation(
			summary = "Read how far the Product Changes have been applied",
			description = "The Catalogue Sync reads the Ingestion's changes after this sequence, and sends them back as a page starting from it.")
	@APIResponse(responseCode = "200", description = "The change sequence of the last change applied.")
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = FORBIDDEN)
	@GET
	@Path("cursor")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<CatalogueChangeCursorResponse> readCursor(@Context ContainerRequestContext crc) {
		return catalogueChangeService.readCursor(currentUser(crc)).map(CatalogueChangeCursorResponse::new);
	}

	@Operation(
			summary = "Apply a page of Product Changes",
			description = "Each Retailer Product becomes exactly what its change describes, and the cursor advances to the end of the page, all in one transaction. Applying the same page again leaves the same state.")
	@APIResponse(responseCode = "204", description = "The page was applied and the cursor advanced to its end.")
	@APIResponse(responseCode = "400", description = "A required field is missing or malformed, a change is outside the sequences of the page, or a Listing names a Catalogue that is unknown or belongs to another Retailer. Nothing was applied.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = FORBIDDEN)
	@APIResponse(responseCode = "409", description = "The page does not start at the cursor, because another sync moved it or the page was read from a stale one. Nothing was applied.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> apply(@Context ContainerRequestContext crc, CatalogueChangesRequest request) {
		var user = currentUser(crc);
		return Uni.createFrom().item(() -> toPage(request)).flatMap(page -> catalogueChangeService.apply(user, page));
	}

	private static ProductChangePage toPage(CatalogueChangesRequest request) {
		if (request == null) throw RequiredInputMissingException.fromRequiredInputName("body");
		return new ProductChangePage(
				request.fromSequence(),
				request.toSequence(),
				request.products() != null ? request.products().stream().map(CatalogueChangesResource::toProductChange).toList() : null
		);
	}

	private static ProductChange toProductChange(ProductChangeDto dto) {
		var builder = ImmutableProductChange.builder()
				.retailerId(new GenericRetailerId(required("retailerId", uuid("retailerId", dto.retailerId()))))
				.productKey(new ProductKeyImpl(requiredText("productKey", dto.productKey())))
				.changeSequence(required("changeSequence", dto.changeSequence()))
				.isRemoved(dto.removed());
		if (!dto.removed()) {
			builder
					.inputHash(requiredText("inputHash", dto.inputHash()))
					.names(required("names", dto.names()))
					.characteristics(orEmpty(dto.characteristics()))
					.tags(orEmpty(dto.tags()))
					.listings(required("listings", dto.listings()).stream().map(CatalogueChangesResource::toListing).toList());
		}
		return built("products", builder::build);
	}

	private static Listing toListing(ListingDto dto) {
		var builder = ImmutableListing.builder()
				.catalogueId(new CatalogueIdImpl(requiredText("catalogueId", dto.catalogueId())))
				.price(required("price", dto.price()))
				.currency(currency(requiredText("currency", dto.currency())))
				.isActive(dto.active());
		return built("listings", builder::build);
	}

	private static Currency currency(String code) {
		try {
			return Currency.getInstance(code);
		} catch (IllegalArgumentException e) {
			throw BadInputValueException.fromInputNameAndHint("currency", "It must be an ISO 4217 code");
		}
	}

	private static <K, V> Map<K, V> orEmpty(Map<K, V> map) {
		return map != null ? map : Map.of();
	}

	private static <T> List<T> orEmpty(List<T> list) {
		return list != null ? list : List.of();
	}
}
