package eu.tealhelix.catalogue.jaxrs.v1.ingestionruns;

import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.INGESTION_RUNS;
import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.INGESTION_RUNS_DESC;
import static eu.tealhelix.common.utils.UniComprehensions.forcm;
import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.RequestValuesHelper.required;
import static eu.tealhelix.common.web.RequestValuesHelper.uuid;

import java.util.List;
import jakarta.inject.Inject;
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
import eu.tealhelix.catalogue.services.v1.IngestionRunService;
import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("ingestion-runs")
@Tag(name = INGESTION_RUNS, description = INGESTION_RUNS_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class IngestionRunsResource {
	private static final String FORBIDDEN = "The caller is neither the Retailer of the run's Catalogue nor an operator.";
	private static final String NOT_AN_OPERATOR = "The caller is not an operator.";
	private static final String NOT_FOUND = "There is no such Ingestion Run.";
	private static final String NOT_HELD = "The run is not held: it never was, or it has been confirmed or discarded already.";

	@Inject
	IngestionRunService ingestionRunService;

	@Operation(summary = "Read how an Ingestion Run stands", description = "Its status, timings and counts.")
	@APIResponse(responseCode = "200", description = "The run.")
	@APIResponse(responseCode = "400", description = "The id is not a UUID.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = FORBIDDEN)
	@APIResponse(responseCode = "404", description = NOT_FOUND,
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@GET
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<IngestionRunDto> find(
			@Context SecurityContext securityContext,
			@Parameter(description = "The id of the run.") @PathParam("id") String id
	) {
		var user = CurrentUser.of(securityContext);
		return forcm(
				Uni.createFrom().item(() -> runId(id)),
				runId -> ingestionRunService.find(user, runId),
				IngestionRunsResource::toDto
		);
	}

	@Operation(summary = "Read the records an Ingestion Run rejected", description = "A page of them, in the order they appear in the Feed. The run's counts tell how many there are.")
	@APIResponse(responseCode = "200", description = "The rejects of the page, empty past the last one.")
	@APIResponse(responseCode = "400", description = "The id is not a UUID, the limit is missing or out of range, or the offset is negative.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = FORBIDDEN)
	@APIResponse(responseCode = "404", description = NOT_FOUND,
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@GET
	@Path("{id}/rejects")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<IngestionRejectDto>> findRejects(
			@Context SecurityContext securityContext,
			@Parameter(description = "The id of the run.") @PathParam("id") String id,
			@Parameter(description = "How many rejects to skip, 0 if left out.") @QueryParam("offset") Integer offset,
			@Parameter(description = "How many rejects to read at most, from 1 to " + IngestionRunService.MAX_REJECTS_PER_PAGE + ".", required = true) @QueryParam("limit") Integer limit
	) {
		var user = CurrentUser.of(securityContext);
		return forcm(
				Uni.createFrom().item(() -> runId(id)),
				runId -> ingestionRunService.findRejects(user, runId, offset, limit),
				rejects -> rejects.stream().map(IngestionRunsResource::toDto).toList()
		);
	}

	@Operation(summary = "Confirm a held Snapshot",
			description = "Takes the active Listings of the Catalogue that the Snapshot did not carry off sale, and ends the run DONE. The later runs of the Catalogue, which waited for it, are processed next.")
	@APIResponse(responseCode = "200", description = "The run, done.")
	@APIResponse(responseCode = "400", description = "The id is not a UUID.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = NOT_AN_OPERATOR)
	@APIResponse(responseCode = "404", description = NOT_FOUND,
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "409", description = NOT_HELD,
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@POST
	@Path("{id}/confirmation")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<IngestionRunDto> confirm(
			@Context SecurityContext securityContext,
			@Parameter(description = "The id of the run.") @PathParam("id") String id
	) {
		var user = CurrentUser.of(securityContext);
		return forcm(
				Uni.createFrom().item(() -> runId(id)),
				runId -> ingestionRunService.confirm(user, runId),
				IngestionRunsResource::toDto
		);
	}

	@Operation(summary = "Discard a held Snapshot",
			description = "Ends the run DISCARDED, taking nothing off sale; the products it applied before it was held stay as they are. The later runs of the Catalogue, which waited for it, are processed next.")
	@APIResponse(responseCode = "200", description = "The run, discarded.")
	@APIResponse(responseCode = "400", description = "The id is not a UUID.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = NOT_AN_OPERATOR)
	@APIResponse(responseCode = "404", description = NOT_FOUND,
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "409", description = NOT_HELD,
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@POST
	@Path("{id}/discard")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<IngestionRunDto> discard(
			@Context SecurityContext securityContext,
			@Parameter(description = "The id of the run.") @PathParam("id") String id
	) {
		var user = CurrentUser.of(securityContext);
		return forcm(
				Uni.createFrom().item(() -> runId(id)),
				runId -> ingestionRunService.discard(user, runId),
				IngestionRunsResource::toDto
		);
	}

	@Operation(summary = "Replay a finished Ingestion Run",
			description = "Records a new run that processes the run's stored Feed again, with the adapter the Catalogue has now, e.g. after a fix of the adapter. "
					+ "The new run shares the Feed's file and receipt, and waits its turn among the runs of the Catalogue in order of receipt; the run replayed stays as it is. "
					+ "A Snapshot is refused once a later Snapshot of its Catalogue has been received and has not failed, since it would take off sale what the later one carries. "
					+ "Any other replay out of order is the operator's to repair, by replaying the later Feeds of the Catalogue as well, in order.")
	@APIResponse(responseCode = "202", description = "The new run, which waits to be processed. The Location header names it.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = IngestionRunDto.class)))
	@APIResponse(responseCode = "400", description = "The id is not a UUID.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = NOT_AN_OPERATOR)
	@APIResponse(responseCode = "404", description = "There is no such Ingestion Run, or its Feed is no longer stored.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "409", description = "The run has not finished: it waits, is processing or is held. Or it is a Snapshot that a later Snapshot supersedes.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@POST
	@Path("{id}/replay")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Response> replay(
			@Context SecurityContext securityContext,
			@Context UriInfo uriInfo,
			@Parameter(description = "The id of the run.") @PathParam("id") String id
	) {
		var user = CurrentUser.of(securityContext);
		var runs = uriInfo.getBaseUriBuilder().path(IngestionRunsResource.class);
		return forcm(
				Uni.createFrom().item(() -> runId(id)),
				runId -> ingestionRunService.replay(user, runId),
				replay -> Response.accepted(toDto(replay)).location(runs.path(replay.getId().asString()).build()).build()
		);
	}

	private static IngestionRunId runId(String id) {
		return new IngestionRunIdImpl(required("id", uuid("id", id)));
	}

	/**
	 * Also maps the runs of a Feed.
	 */
	public static IngestionRunDto toDto(IngestionRun run) {
		var counts = run.getCounts();
		return new IngestionRunDto(
				run.getId().asString(),
				run.getFeed().getId().asString(),
				run.getCatalogueId().asString(),
				run.getFeed().getFeedType().name(),
				run.getStatus().name(),
				run.getAdapter(),
				run.getAdapterVersion(),
				run.getFeed().getReceivedAt(),
				run.getStartedAt(),
				run.getFinishedAt(),
				counts != null
						? new IngestionRunCountsDto(counts.getTotal(), counts.getValid(), counts.getRejected(), counts.getAdded(), counts.getChanged(), counts.getUnchanged(), counts.getMissing(), counts.getDeactivated())
						: null,
				run.getError(),
				run.getReplayOfRunId() != null ? run.getReplayOfRunId().asString() : null
		);
	}

	private static IngestionRejectDto toDto(IngestionReject reject) {
		return new IngestionRejectDto(reject.getPosition(), reject.getProductKey() != null ? reject.getProductKey().asString() : null, reject.getReason());
	}
}
