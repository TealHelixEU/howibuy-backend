package eu.tealhelix.catalogue.jaxrs.v1.feeds;

import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.FEEDS;
import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.FEEDS_DESC;
import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;

import java.io.InputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

import eu.tealhelix.catalogue.jaxrs.authentication.CurrentUser;
import eu.tealhelix.catalogue.jaxrs.v1.ingestionruns.IngestionRunsResource;
import eu.tealhelix.catalogue.services.v1.FeedService;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("catalogues/{catalogueId}/feeds")
@Tag(name = FEEDS, description = FEEDS_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class FeedsResource {
	@Inject
	FeedService feedService;

	/**
	 * Reading the body blocks, so this runs on a worker thread; the body is stored as it arrives.
	 */
	@Operation(
			summary = "Push a Feed of a Catalogue",
			description = "The body is the Feed as the Retailer's systems export it, in any format the Retailer's adapter reads. It is stored as it arrives and answered at once; the Ingestion Run processes it later.")
	@APIResponse(responseCode = "202", description = "The Feed was stored, and its Ingestion Run waits to be processed. The Location header names the run.")
	@APIResponse(responseCode = "400", description = "The type of the Feed is missing or unknown.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = "The caller is not a Retailer, or the Catalogue is another Retailer's or does not accept Feeds.")
	@APIResponse(responseCode = "404", description = "There is no such Catalogue.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "413", description = "The Feed is larger than the Ingestion accepts.")
	@POST
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	public Response receive(
			@Context SecurityContext securityContext,
			@Context UriInfo uriInfo,
			@Parameter(description = "The Catalogue, one of the caller's, e.g. 'rimi-lt'.")
			@PathParam("catalogueId") String catalogueId,
			@Parameter(description = "What the Feed tells about its Catalogue: SNAPSHOT for the whole Catalogue, DELTA for some of its products, DELETION for products taken off sale.", required = true)
			@QueryParam("type") String type,
			@RequestBody(description = "The Feed.", required = true)
			InputStream body
	) {
		var feedType = feedType(type);
		var run = feedService.receive(CurrentUser.of(securityContext), new CatalogueIdImpl(catalogueId), feedType, body);
		var statusUrl = uriInfo.getBaseUriBuilder().path(IngestionRunsResource.class).path(run.getId().asString()).build();
		return Response.accepted(new FeedReceiptResponse(run.getId().asString(), run.getStatus().name(), statusUrl.toString()))
				.location(statusUrl)
				.build();
	}

	private static FeedType feedType(String type) {
		RequiredInputMissingException.throwIfRequiredInputMissing("type", type);
		try {
			return FeedType.valueOf(type);
		} catch (IllegalArgumentException e) {
			String allowedValues = Stream.of(FeedType.values()).map(Enum::name).collect(Collectors.joining(", "));
			throw BadInputValueException.fromInputNameAndHint("type", "It must be one of " + allowedValues);
		}
	}
}
