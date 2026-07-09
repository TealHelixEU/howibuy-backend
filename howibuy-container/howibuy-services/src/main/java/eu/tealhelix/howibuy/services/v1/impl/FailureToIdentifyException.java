package eu.tealhelix.howibuy.services.v1.impl;

import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcomeDiagnostics;
import eu.tealhelix.howibuy.v1.model.ProductData;

public class FailureToIdentifyException extends Exception {
	private final ProductData productData;
	private final ProductAssessmentOutcomeDiagnostics diagnostics;

	public FailureToIdentifyException(ProductData productData, ProductAssessmentOutcomeDiagnostics diagnostics) {
		this.productData = productData;
		this.diagnostics = diagnostics;
	}

	public ProductData getProductData() {
		return productData;
	}

	public ProductAssessmentOutcomeDiagnostics getDiagnostics() {
		return diagnostics;
	}
}
