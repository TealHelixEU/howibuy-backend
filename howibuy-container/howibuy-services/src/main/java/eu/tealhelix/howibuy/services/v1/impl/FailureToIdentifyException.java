package eu.tealhelix.howibuy.services.v1.impl;

import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcomeDiagnostics;

/**
 * Signals, on the {@code Uni} failure channel, that the assessment descent could not match the product at some
 * taxonomy level, carrying the diagnostics accumulated up to that point. It is a control-flow signal that lets the
 * flat {@code forc} descent short-circuit and be turned into a {@code FAILURE_TO_IDENTIFY} outcome by a single
 * {@code recoverWithUni} at the top of {@code assessSingleProduct} — not a real error.
 * <p>
 * {@link #fillInStackTrace()} is overridden to a no-op because the stack trace is never read: it makes the
 * control-flow intent explicit to readers and avoids the cost of capturing a stack on every no-match.
 */
public class FailureToIdentifyException extends RuntimeException {
	private final ProductAssessmentOutcomeDiagnostics diagnostics;

	public FailureToIdentifyException(ProductAssessmentOutcomeDiagnostics diagnostics) {
		this.diagnostics = diagnostics;
	}

	public ProductAssessmentOutcomeDiagnostics getDiagnostics() {
		return diagnostics;
	}

	@Override
	public Throwable fillInStackTrace() {
		return this;
	}
}
