package eu.tealhelix.common.web.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.tealhelix.common.types.entity.NotFoundException;

/**
 * Map the {@link NotFoundException} of TealHelix to HTTP 404 (NOT FOUND).
 */
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {
	@Override
	public Response toResponse(NotFoundException e) {
		return Response.status(Response.Status.NOT_FOUND).entity(new SingleMessageResponse(e.getMessage())).build();
	}
}
