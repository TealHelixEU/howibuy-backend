package eu.tealhelix.sfc.jaxrs.v1.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.tealhelix.sfc.services.v1.types.StabilityWindowActiveException;

/**
 * Maps an answer attempted while a prior completed attempt is still within its stability window to HTTP 409 (Conflict),
 * returning when a fresh attempt becomes possible.
 */
@Provider
public class StabilityWindowActiveExceptionMapper implements ExceptionMapper<StabilityWindowActiveException> {
	@Override
	public Response toResponse(StabilityWindowActiveException e) {
		return Response.status(Response.Status.CONFLICT).entity(new StabilityWindowResponse(e.getMessage(), e.getEligibleAt())).build();
	}
}
