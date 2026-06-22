package eu.tealhelix.common.web.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.tealhelix.common.types.validation.BadInputValueException;

@Provider
public class BadInputValueExceptionMapper implements ExceptionMapper<BadInputValueException> {
	@Override
	public Response toResponse(BadInputValueException e) {
		return Response.status(Response.Status.BAD_REQUEST).entity(new SingleMessageResponse(e.getMessage())).build();
	}
}
