package eu.tealhelix.cataloguesync.ingestion;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.oidc.client.filter.OidcClientFilter;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * The Product Changes of the Ingestion, read with the token of the Catalogue Sync's service account.
 */
@Path("/api/catalogue/v1/product-changes")
@RegisterRestClient(configKey = "ingestion")
@OidcClientFilter
public interface IngestionClient {
	/**
	 * @param after The change sequence to read after
	 * @param limit How many products to read at most
	 * @return The page of the products that changed after {@code after}, empty if none did
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	ProductChangePageResponse findChanges(@QueryParam("after") long after, @QueryParam("limit") int limit);
}
