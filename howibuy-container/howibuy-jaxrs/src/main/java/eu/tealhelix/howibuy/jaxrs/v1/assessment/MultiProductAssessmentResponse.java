package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import java.util.List;

import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;

public record MultiProductAssessmentResponse(List<ProductAssessmentOutcome> assessments) {
}
