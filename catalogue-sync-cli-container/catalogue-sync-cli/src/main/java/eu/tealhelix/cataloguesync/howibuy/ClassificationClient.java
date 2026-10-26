package eu.tealhelix.cataloguesync.howibuy;

import java.util.List;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.oidc.client.filter.OidcClientFilter;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * The products HowiBuy has waiting to be classified, and their classification by its LLM, with the token of the
 * Catalogue Sync's service account.
 */
@Path("/api/howibuy/v1")
@RegisterRestClient(configKey = "howibuy")
@OidcClientFilter
public interface ClassificationClient {
	/**
	 * @param limit How many products to read at most
	 * @return The pending products, the one changed longest ago first
	 */
	@GET
	@Path("classifications/pending")
	@Produces(MediaType.APPLICATION_JSON)
	List<PendingProductResponse> findPending(@QueryParam("limit") int limit);

	/**
	 * Takes as long as the LLM takes to match the product, which may be minutes.
	 */
	@POST
	@Path("llm-classifications")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	LlmClassificationResponse classify(LlmClassificationRequest request);
}
