package eu.tealhelix.sfc.jaxrs.v1;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.JaxRsUtils.currentUser;
import static eu.tealhelix.sfc.jaxrs.v1.SfcOpenApiTagNames.SFC_ATTEMPTS;
import static eu.tealhelix.sfc.jaxrs.v1.SfcOpenApiTagNames.SFC_ATTEMPTS_DESC;

import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import eu.tealhelix.sfc.jaxrs.v1.exceptionmap.IncompleteAttemptResponse;
import eu.tealhelix.sfc.jaxrs.v1.exceptionmap.StabilityWindowResponse;
import eu.tealhelix.sfc.services.v1.CompassAttemptService;
import eu.tealhelix.sfc.v1.types.impl.QuestionIdImpl;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Writes to the user's Sustainable Food Compass attempt — recording (or overwriting) the answer to a question, saved
 * immediately, starting a fresh blank attempt for a user taking the compass again, and completing the attempt (freezing
 * it as an immutable record). Requires an authenticated end-user.
 */
@Path("sfc")
@Tag(name = SFC_ATTEMPTS, description = SFC_ATTEMPTS_DESC)
@SecurityRequirement(name = BEARER_AUTH)
@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
@APIResponse(responseCode = "403", description = "A service account presented the token: an attempt belongs to an end-user.")
public class CompassAttemptResource {
	@Inject
	CompassAttemptService compassAttemptService;

	@Operation(
			summary = "Answer a question, or change the answer already given",
			description = "The answer is saved at once, so the user can stop and pick the compass up later. The first answer of a user with no attempt in progress starts one; answering a question again overwrites the choice made before.")
	@APIResponse(responseCode = "204", description = "The answer was saved.")
	@APIResponse(responseCode = "400", description = "Malformed request or no option was given.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "409", description = "There is nothing in progress to answer and a completed attempt is still inside its stability window. The answer names the moment the window ends, the earliest a fresh attempt can be started by answering.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = StabilityWindowResponse.class)))
	@PUT
	@Path("questions/{questionId}/answer")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> answer(
			@Context ContainerRequestContext crc,
			@Parameter(description = "The question being answered, as its id reads in the questions of a category.") @PathParam("questionId") UUID questionId,
			AnswerRequest request) {
		var option = request == null ? null : request.option();
		return compassAttemptService.answer(currentUser(crc), new QuestionIdImpl(questionId.toString()), option);
	}

	@Operation(
			summary = "Start a fresh attempt",
			description = "The deliberate way for a user to take the compass again, rather than waiting for an answer to bring an attempt into being.")
	@APIResponse(responseCode = "204", description = "A fresh, blank attempt is in progress.")
	@APIResponse(responseCode = "409", description = "Either an attempt is already in progress, or a completed one is still inside its stability window — in which case the answer also names the moment that window ends.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(anyOf = {StabilityWindowResponse.class, SingleMessageResponse.class})))
	@POST
	@Path("attempts")
	public Uni<Void> startNewAttempt(@Context ContainerRequestContext crc) {
		return compassAttemptService.startNewAttempt(currentUser(crc));
	}

	@Operation(
			summary = "Complete the attempt in progress",
			description = "Checks that every question is answered, then freezes the attempt and stamps the time. Completion is always an explicit act: an attempt does not complete itself on the last answer.")
	@APIResponse(responseCode = "204", description = "The attempt is complete and from now on immutable.")
	@APIResponse(responseCode = "409", description = "The user has no attempt in progress to complete.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "422", description = "Questions are still unanswered. The answer names exactly which, so the client can walk the user through the rest.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = IncompleteAttemptResponse.class)))
	@POST
	@Path("attempts/current/completion")
	public Uni<Void> complete(@Context ContainerRequestContext crc) {
		return compassAttemptService.complete(currentUser(crc));
	}
}
