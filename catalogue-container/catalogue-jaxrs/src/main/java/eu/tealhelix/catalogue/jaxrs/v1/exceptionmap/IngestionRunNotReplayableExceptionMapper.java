package eu.tealhelix.catalogue.jaxrs.v1.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.tealhelix.catalogue.services.v1.types.IngestionRunNotReplayableException;
import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;

/**
 * Maps a replay of an Ingestion Run that is not replayed now to HTTP 409 (Conflict).
 */
@Provider
public class IngestionRunNotReplayableExceptionMapper implements ExceptionMapper<IngestionRunNotReplayableException> {
	@Override
	public Response toResponse(IngestionRunNotReplayableException e) {
		return Response.status(Response.Status.CONFLICT).entity(new SingleMessageResponse(e.getMessage())).build();
	}
}
