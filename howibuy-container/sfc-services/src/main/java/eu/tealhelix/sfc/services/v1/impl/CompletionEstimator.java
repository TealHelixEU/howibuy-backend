package eu.tealhelix.sfc.services.v1.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The configured per-question time used to estimate how long completing the compass takes ({@code
 * sfc.seconds-per-question}). Given a number of questions it returns the estimated seconds — the whole compass or a
 * single category — as {@code seconds-per-question × question count}.
 */
@ApplicationScoped
public class CompletionEstimator {
	private final int secondsPerQuestion;

	@Inject
	public CompletionEstimator(@ConfigProperty(name = "sfc.seconds-per-question") int secondsPerQuestion) {
		this.secondsPerQuestion = secondsPerQuestion;
	}

	/**
	 * The estimated seconds to answer {@code questionCount} questions.
	 */
	public long secondsFor(int questionCount) {
		return (long) secondsPerQuestion * questionCount;
	}
}
