package eu.tealhelix.howibuy;

import static eu.tealhelix.howibuy.OpenApiTagNames.MISCELLANEOUS;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.HANDOFF;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.HANDOFF_DESC;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.PRODUCT_ASSESSMENT;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.PRODUCT_ASSESSMENT_DESC;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.TOKEN_EXCHANGE;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.TOKEN_EXCHANGE_DESC;
import static eu.tealhelix.sfc.jaxrs.v1.SfcOpenApiTagNames.SFC;
import static eu.tealhelix.sfc.jaxrs.v1.SfcOpenApiTagNames.SFC_ATTEMPTS;
import static eu.tealhelix.sfc.jaxrs.v1.SfcOpenApiTagNames.SFC_ATTEMPTS_DESC;
import static eu.tealhelix.sfc.jaxrs.v1.SfcOpenApiTagNames.SFC_DESC;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;


@ApplicationPath(JaxRsAppHowiBuyV1.APPLICATION_PATH)
@OpenAPIDefinition(
		info = @Info(
				title = "HowiBuy API",
				version = "0.1.0", // API versioning, distinct from the application versioning
				description = "HowiBuy backend: product sustainability assessment and the Sustainable Food Compass."
		),
		tags = {
				@Tag(name = PRODUCT_ASSESSMENT, description = PRODUCT_ASSESSMENT_DESC),
				@Tag(name = TOKEN_EXCHANGE, description = TOKEN_EXCHANGE_DESC),
				@Tag(name = HANDOFF, description = HANDOFF_DESC),
				@Tag(name = SFC, description = SFC_DESC),
				@Tag(name = SFC_ATTEMPTS, description = SFC_ATTEMPTS_DESC),
				@Tag(name = MISCELLANEOUS),
		}
)
public class JaxRsAppHowiBuyV1 extends Application {
	/**
	 * The JAX-RS application path.
	 */
	public static final String APPLICATION_PATH = "/api/howibuy/v1";
}
