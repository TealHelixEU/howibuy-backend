package eu.tealhelix.common.web.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.tealhelix.common.types.authorization.NotAuthorizedException;

/**
 * Map the {@link NotAuthorizedException} of TealHelix to HTTP 403 (FORBIDDEN).
 */
@Provider
public class NotAuthorizedExceptionMapper implements ExceptionMapper<NotAuthorizedException> {
	@Override
	public Response toResponse(NotAuthorizedException exception) {
		return Response.status(Response.Status.FORBIDDEN).build();
	}
}
