package eu.tealhelix.howibuy.services.v1.impl;

import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcomeDiagnostics;

/**
 * Signals, on the {@code Uni} failure channel, that the AI-driven taxonomy descent could not produce a successful
 * assessment for a product, carrying the diagnostics accumulated up to that point. It is a control-flow signal that
 * lets the flat {@code forc} descent short-circuit; a single {@code recoverWithItem} per product turns each subtype
 * into the matching non-{@code SUCCESS} outcome — it is not a real error, and never escapes the service as a 500.
 * <p>
 * The type is {@code sealed} so the recovery can pattern-switch over the subtypes exhaustively: adding a new failure
 * shape becomes a compile error until it is handled.
 * <p>
 * {@link #fillInStackTrace()} is overridden to a no-op because the stack trace is never read: it makes the
 * control-flow intent explicit to readers and avoids the cost of capturing a stack on every occurrence.
 */
public abstract sealed class ProductNotAssessedException extends RuntimeException
		permits FailureToIdentifyException, AiPickNotAmongCandidatesException {
	private final ProductAssessmentOutcomeDiagnostics diagnostics;

	protected ProductNotAssessedException(ProductAssessmentOutcomeDiagnostics diagnostics) {
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
