package eu.tealhelix.howibuy.services.v1.impl;

import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcomeDiagnostics;

/**
 * The AI replied with a name that is neither the {@code NONE} sentinel nor one of the candidate names it was given —
 * it did not honour the prompt contract. Unlike {@link FailureToIdentifyException} this is not a business no-match but
 * an AI malfunction; it is reported to the caller as {@code FAILURE_OTHER} and logged so its rate can be monitored.
 */
public final class AiPickNotAmongCandidatesException extends ProductNotAssessedException {
	private final String kind;
	private final String pickedName;

	public AiPickNotAmongCandidatesException(ProductAssessmentOutcomeDiagnostics diagnostics, String kind, String pickedName) {
		super(diagnostics);
		this.kind = kind;
		this.pickedName = pickedName;
	}

	public String getKind() {
		return kind;
	}

	public String getPickedName() {
		return pickedName;
	}
}
