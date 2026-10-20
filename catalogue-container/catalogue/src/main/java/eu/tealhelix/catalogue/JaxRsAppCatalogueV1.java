package eu.tealhelix.catalogue;

import static eu.tealhelix.catalogue.OpenApiTagNames.MISCELLANEOUS;
import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.FEEDS;
import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.FEEDS_DESC;
import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.INGESTION_RUNS;
import static eu.tealhelix.catalogue.jaxrs.v1.CatalogueOpenApiTagNames.INGESTION_RUNS_DESC;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;


@ApplicationPath(JaxRsAppCatalogueV1.APPLICATION_PATH)
@OpenAPIDefinition(
		info = @Info(
				title = "Catalogue API",
				version = "0.1.0", // API versioning, distinct from the application versioning
				description = "The Ingestion of Retailer Catalogues: receives the Feeds of the Retailers and keeps their Catalogues current."
		),
		tags = {
				@Tag(name = FEEDS, description = FEEDS_DESC),
				@Tag(name = INGESTION_RUNS, description = INGESTION_RUNS_DESC),
				@Tag(name = MISCELLANEOUS),
		}
)
public class JaxRsAppCatalogueV1 extends Application {
	/**
	 * The JAX-RS application path.
	 */
	public static final String APPLICATION_PATH = "/api/catalogue/v1";
}
