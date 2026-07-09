package eu.tealhelix.howibuy.services.v1.impl;

import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcomeDiagnostics;

/**
 * The AI deliberately reported no match (its reply was the {@code NONE} sentinel) at some taxonomy level. This is a
 * legitimate business outcome — the product could not be identified among our archetypes — reported to the caller as
 * {@code FAILURE_TO_IDENTIFY}.
 */
public final class FailureToIdentifyException extends ProductNotAssessedException {
	public FailureToIdentifyException(ProductAssessmentOutcomeDiagnostics diagnostics) {
		super(diagnostics);
	}
}
