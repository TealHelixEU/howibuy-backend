package eu.tealhelix.cataloguesync.howibuy;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkus.oidc.client.filter.OidcClientFilter;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * The Product Changes HowiBuy has applied, and the application of more, with the token of the Catalogue Sync's service
 * account.
 */
@Path("/api/howibuy/v1/catalogue-changes")
@RegisterRestClient(configKey = "howibuy")
@OidcClientFilter
public interface HowiBuyClient {
	@GET
	@Path("cursor")
	@Produces(MediaType.APPLICATION_JSON)
	CatalogueChangeCursorResponse readCursor();

	/**
	 * @throws CursorConflictException If the page does not start at HowiBuy's cursor
	 */
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	void apply(CatalogueChangesRequest request);

	/**
	 * @return The exception of a refused page, or null to leave every other error to the REST client
	 */
	@ClientExceptionMapper
	static RuntimeException toException(Response response) {
		if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
			return new CursorConflictException("HowiBuy refused the page, because it does not start at its cursor");
		} else {
			return null;
		}
	}
}
