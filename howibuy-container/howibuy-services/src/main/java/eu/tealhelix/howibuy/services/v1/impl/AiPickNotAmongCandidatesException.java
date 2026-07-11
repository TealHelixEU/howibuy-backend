package eu.tealhelix.howibuy.services.v1.impl;

import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcomeDiagnostics;

/**
 * The AI's reply was neither the no-match token nor a candidate number within the range it was shown — it did not
 * honour the prompt contract. Unlike {@link FailureToIdentifyException} this is not a business no-match but an AI
 * malfunction; it is reported to the caller as {@code FAILURE_OTHER} and logged so its rate can be monitored.
 */
public final class AiPickNotAmongCandidatesException extends ProductNotAssessedException {
	private final String kind;
	private final String rawReply;

	public AiPickNotAmongCandidatesException(ProductAssessmentOutcomeDiagnostics diagnostics, String kind, String rawReply) {
		super(diagnostics);
		this.kind = kind;
		this.rawReply = rawReply;
	}

	public String getKind() {
		return kind;
	}

	public String getRawReply() {
		return rawReply;
	}
}
