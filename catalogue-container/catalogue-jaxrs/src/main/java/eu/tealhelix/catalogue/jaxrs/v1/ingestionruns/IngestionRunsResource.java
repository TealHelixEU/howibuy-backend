package eu.tealhelix.catalogue.jaxrs.v1.ingestionruns;

import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.INGESTION_RUNS;
import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.INGESTION_RUNS_DESC;
import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;

import java.util.List;
import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

import eu.tealhelix.catalogue.jaxrs.authentication.CurrentUser;
import eu.tealhelix.catalogue.services.v1.IngestionRunService;
import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
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
	private static final String NOT_FOUND = "There is no such Ingestion Run.";

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
		return Uni.createFrom().item(() -> runId(id))
				.flatMap(runId -> ingestionRunService.find(user, runId))
				.map(IngestionRunsResource::toDto);
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
		return Uni.createFrom().item(() -> runId(id))
				.flatMap(runId -> ingestionRunService.findRejects(user, runId, offset != null ? offset : 0, required("limit", limit)))
				.map(rejects -> rejects.stream().map(IngestionRunsResource::toDto).toList());
	}

	private static IngestionRunId runId(String id) {
		try {
			return new IngestionRunIdImpl(UUID.fromString(id).toString());
		} catch (IllegalArgumentException e) {
			throw BadInputValueException.fromInputNameAndHint("id", "It must be a UUID");
		}
	}

	private static <T> T required(String inputName, T value) {
		if (value == null) throw RequiredInputMissingException.fromRequiredInputName(inputName);
		return value;
	}

	private static IngestionRunDto toDto(IngestionRun run) {
		var counts = run.getCounts();
		return new IngestionRunDto(
				run.getId().asString(),
				run.getCatalogueId().asString(),
				run.getFeedType().name(),
				run.getStatus().name(),
				run.getAdapter(),
				run.getAdapterVersion(),
				run.getReceivedAt(),
				run.getStartedAt(),
				run.getFinishedAt(),
				run.getFileSize(),
				run.getFileSha256(),
				counts != null
						? new IngestionRunCountsDto(counts.getTotal(), counts.getValid(), counts.getRejected(), counts.getAdded(), counts.getChanged(), counts.getUnchanged(), counts.getDeactivated())
						: null,
				run.getError()
		);
	}

	private static IngestionRejectDto toDto(IngestionReject reject) {
		return new IngestionRejectDto(reject.getPosition(), reject.getProductKey() != null ? reject.getProductKey().asString() : null, reject.getReason());
	}
}
