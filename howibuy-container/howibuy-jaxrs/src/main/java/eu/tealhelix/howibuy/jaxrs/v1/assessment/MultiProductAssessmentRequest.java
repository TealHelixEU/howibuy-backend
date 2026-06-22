package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import java.util.List;

import eu.tealhelix.howibuy.v1.model.ProductData;

public record MultiProductAssessmentRequest(List<ProductData> products) {
}
