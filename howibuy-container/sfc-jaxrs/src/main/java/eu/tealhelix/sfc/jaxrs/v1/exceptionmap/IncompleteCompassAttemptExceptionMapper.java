package eu.tealhelix.sfc.jaxrs.v1.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.tealhelix.sfc.services.v1.types.IncompleteCompassAttemptException;

/**
 * Maps an attempted completion with unanswered questions to HTTP 422 (Unprocessable Entity), returning the unanswered
 * question ids.
 */
@Provider
public class IncompleteCompassAttemptExceptionMapper implements ExceptionMapper<IncompleteCompassAttemptException> {
	private static final int UNPROCESSABLE_ENTITY = 422;

	@Override
	public Response toResponse(IncompleteCompassAttemptException e) {
		return Response.status(UNPROCESSABLE_ENTITY).entity(new IncompleteAttemptResponse(e.getUnansweredQuestionIds())).build();
	}
}
