package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import java.util.List;

import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The outcomes of a batch assessment, one per product of the request and in the same order.")
public record MultiProductAssessmentResponse(
		@Schema(description = "One outcome per product sent. A product that could not be assessed takes a failure outcome of its own rather than bringing the batch down.")
		List<ProductAssessmentOutcome> assessments
) {
}
