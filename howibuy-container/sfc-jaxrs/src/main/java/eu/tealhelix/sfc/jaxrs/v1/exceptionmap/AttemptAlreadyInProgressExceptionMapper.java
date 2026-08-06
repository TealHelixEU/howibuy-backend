package eu.tealhelix.sfc.jaxrs.v1.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import eu.tealhelix.sfc.services.v1.types.AttemptAlreadyInProgressException;

/**
 * Maps a request for a fresh attempt while one is already in progress to HTTP 409 (Conflict).
 */
@Provider
public class AttemptAlreadyInProgressExceptionMapper implements ExceptionMapper<AttemptAlreadyInProgressException> {
	@Override
	public Response toResponse(AttemptAlreadyInProgressException e) {
		return Response.status(Response.Status.CONFLICT).entity(new SingleMessageResponse(e.getMessage())).build();
	}
}
