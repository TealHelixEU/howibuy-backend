package eu.tealhelix.howibuy.jaxrs.v1.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import eu.tealhelix.howibuy.services.v1.types.ChangeCursorConflictException;

/**
 * Maps a page of Product Changes that does not start at the change cursor to HTTP 409 (Conflict).
 */
@Provider
public class ChangeCursorConflictExceptionMapper implements ExceptionMapper<ChangeCursorConflictException> {
	@Override
	public Response toResponse(ChangeCursorConflictException e) {
		return Response.status(Response.Status.CONFLICT).entity(new SingleMessageResponse(e.getMessage())).build();
	}
}
