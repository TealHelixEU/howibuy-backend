package eu.tealhelix.howibuy.jaxrs.v1.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import eu.tealhelix.howibuy.services.v1.types.InputHashConflictException;

/**
 * Maps a Classification Attempt made from another input hash than the product's current one to HTTP 409 (Conflict).
 */
@Provider
public class InputHashConflictExceptionMapper implements ExceptionMapper<InputHashConflictException> {
	@Override
	public Response toResponse(InputHashConflictException e) {
		return Response.status(Response.Status.CONFLICT).entity(new SingleMessageResponse(e.getMessage())).build();
	}
}
