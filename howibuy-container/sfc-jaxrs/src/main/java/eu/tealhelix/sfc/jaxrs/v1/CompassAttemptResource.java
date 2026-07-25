package eu.tealhelix.sfc.jaxrs.v1;

import static eu.tealhelix.common.web.JaxRsUtils.currentUser;

import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.sfc.services.v1.CompassAttemptService;
import io.smallrye.mutiny.Uni;

/**
 * Writes to the user's Sustainable Food Compass attempt — recording (or overwriting) the answer to a question, saved
 * immediately. Requires an authenticated end-user.
 */
@Path("sfc")
public class CompassAttemptResource {
	@Inject
	CompassAttemptService compassAttemptService;

	@PUT
	@Path("questions/{questionId}/answer")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> answer(@Context ContainerRequestContext crc, @PathParam("questionId") UUID questionId, AnswerRequest request) {
		var option = request == null ? null : request.option();
		return compassAttemptService.answer(currentUser(crc), questionId, option);
	}
}
