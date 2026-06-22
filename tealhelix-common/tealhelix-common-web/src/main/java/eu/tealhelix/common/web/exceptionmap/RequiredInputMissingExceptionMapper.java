package eu.tealhelix.common.web.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.tealhelix.common.types.validation.RequiredInputMissingException;

@Provider
public class RequiredInputMissingExceptionMapper implements ExceptionMapper<RequiredInputMissingException> {
	@Override
	public Response toResponse(RequiredInputMissingException e) {
		return Response.status(Response.Status.BAD_REQUEST).entity(new SingleMessageResponse(e.getMessage())).build();
	}
}
