package eu.tealhelix.howibuy.jaxrs.v1.classification;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "How many Classifications were reopened.")
public record ClassificationReopeningResponse(
		@Schema(description = "The Classifications reopened, whose products are pending again.")
		int reopened
) {
}
