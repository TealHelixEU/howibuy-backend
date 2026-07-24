package eu.tealhelix.common.web.exceptionmap;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.tealhelix.common.types.authorization.NotAuthenticatedException;

/**
 * Map the {@link NotAuthenticatedException} of TealHelix to HTTP 401 (UNAUTHORIZED), with a {@code Bearer}
 * authentication challenge.
 */
@Provider
public class NotAuthenticatedExceptionMapper implements ExceptionMapper<NotAuthenticatedException> {
	@Override
	public Response toResponse(NotAuthenticatedException exception) {
		return Response.status(Response.Status.UNAUTHORIZED)
				.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
				.build();
	}
}
