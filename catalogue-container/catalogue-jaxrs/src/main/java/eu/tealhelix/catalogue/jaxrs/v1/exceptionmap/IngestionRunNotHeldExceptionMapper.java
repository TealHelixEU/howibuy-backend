package eu.tealhelix.catalogue.jaxrs.v1.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.tealhelix.catalogue.services.v1.types.IngestionRunNotHeldException;
import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;

/**
 * Maps a decision on an Ingestion Run that is not held to HTTP 409 (Conflict).
 */
@Provider
public class IngestionRunNotHeldExceptionMapper implements ExceptionMapper<IngestionRunNotHeldException> {
	@Override
	public Response toResponse(IngestionRunNotHeldException e) {
		return Response.status(Response.Status.CONFLICT).entity(new SingleMessageResponse(e.getMessage())).build();
	}
}
