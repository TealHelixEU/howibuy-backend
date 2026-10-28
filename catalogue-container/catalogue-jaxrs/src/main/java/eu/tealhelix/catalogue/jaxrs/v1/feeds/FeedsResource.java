package eu.tealhelix.catalogue.jaxrs.v1.feeds;

import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.FEEDS;
import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.FEEDS_DESC;
import static eu.tealhelix.common.utils.UniComprehensions.forcm;
import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.RequestValuesHelper.uuid;

import java.io.InputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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
import eu.tealhelix.catalogue.services.v1.types.FeedWithRuns;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.FeedIdImpl;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("feeds")
@Tag(name = FEEDS, description = FEEDS_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class FeedsResource {
	private static final String ACCEPTED = "The Feed was stored, and its Ingestion Runs wait to be processed. The Location header names the Feed.";
	private static final String BAD_TYPE = "The type of the Feed is missing or unknown.";
	private static final String TOO_LARGE = "The Feed is larger than the Ingestion accepts.";
	private static final String TYPE = "What the Feed tells about its Catalogues: SNAPSHOT for the whole Catalogue, DELTA for some of its products, DELETION for products taken off sale.";

	@Inject
	FeedService feedService;

	/**
	 * Reading the body blocks, so this runs on a worker thread; the body is stored as it arrives.
	 */
	@Operation(
			summary = "Push a Feed for every active Catalogue",
			description = "For a Retailer that exports one file for all its Catalogues. The body is the Feed as the Retailer's systems export it, in any format the Retailer's adapter reads. It is stored once as it arrives and answered at once with one Ingestion Run per active Catalogue of the Retailer, each processed later on its own.")
	@APIResponse(responseCode = "202", description = ACCEPTED)
	@APIResponse(responseCode = "400", description = BAD_TYPE,
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = "The caller is not a Retailer, or has no active Catalogue.")
	@APIResponse(responseCode = "413", description = TOO_LARGE)
	@POST
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	public Response receiveForActiveCatalogues(
			@Context SecurityContext securityContext,
			@Context UriInfo uriInfo,
			@Parameter(description = TYPE, required = true)
			@QueryParam("type") String type,
			@RequestBody(description = "The Feed.", required = true)
			InputStream body
	) {
		var feedType = feedType(type);
		return accepted(uriInfo, feedService.receiveForActiveCatalogues(CurrentUser.of(securityContext), feedType, body));
	}

	/**
	 * Reading the body blocks, so this runs on a worker thread; the body is stored as it arrives.
	 */
	@Operation(
			summary = "Push a Feed of a Catalogue",
			description = "The body is the Feed as the Retailer's systems export it, in any format the Retailer's adapter reads. It is stored as it arrives and answered at once with its Ingestion Run, which is processed later.")
	@APIResponse(responseCode = "202", description = ACCEPTED)
	@APIResponse(responseCode = "400", description = BAD_TYPE,
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = "The caller is not a Retailer, or the Catalogue is another Retailer's or does not accept Feeds.")
	@APIResponse(responseCode = "404", description = "There is no such Catalogue.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "413", description = TOO_LARGE)
	@POST
	@Path("catalogues/{catalogueId}")
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	public Response receive(
			@Context SecurityContext securityContext,
			@Context UriInfo uriInfo,
			@Parameter(description = "The Catalogue, one of the caller's, e.g. 'rimi-lt'.")
			@PathParam("catalogueId") String catalogueId,
			@Parameter(description = TYPE, required = true)
			@QueryParam("type") String type,
			@RequestBody(description = "The Feed.", required = true)
			InputStream body
	) {
		var feedType = feedType(type);
		return accepted(uriInfo, feedService.receive(CurrentUser.of(securityContext), new CatalogueIdImpl(catalogueId), feedType, body));
	}

	@Operation(summary = "Read a Feed and how its Ingestion Runs stand", description = "The file as received, and the status, timings and counts of each of its runs.")
	@APIResponse(responseCode = "200", description = "The Feed and its runs.")
	@APIResponse(responseCode = "400", description = "The id is not a UUID.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = "The caller is neither the Retailer that sent the Feed nor an operator.")
	@APIResponse(responseCode = "404", description = "There is no such Feed.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@GET
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<FeedDto> find(
			@Context SecurityContext securityContext,
			@Parameter(description = "The id of the Feed.") @PathParam("id") String id
	) {
		var user = CurrentUser.of(securityContext);
		return forcm(
				Uni.createFrom().item(() -> new FeedIdImpl(uuid("id", id))),
				feedId -> feedService.find(user, feedId),
				FeedsResource::toDto
		);
	}

	private static Response accepted(UriInfo uriInfo, FeedWithRuns received) {
		var feedUrl = uriInfo.getBaseUriBuilder().path(FeedsResource.class).path(received.feed().getId().asString()).build();
		return Response.accepted(toDto(received)).location(feedUrl).build();
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

	private static FeedDto toDto(FeedWithRuns received) {
		var feed = received.feed();
		return new FeedDto(
				feed.getId().asString(),
				feed.getFeedType().name(),
				feed.getReceivedAt(),
				feed.getFileSize(),
				feed.getFileSha256(),
				received.runs().stream().map(IngestionRunsResource::toDto).toList()
		);
	}
}
