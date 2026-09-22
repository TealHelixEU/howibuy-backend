package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import java.util.List;

import eu.tealhelix.howibuy.v1.model.ProductData;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The products to assess in one request.")
public record MultiProductAssessmentRequest(
		@Schema(description = "The products to assess. Each one is answered by an outcome of its own, in the order sent.")
		List<ProductData> products
) {
}
