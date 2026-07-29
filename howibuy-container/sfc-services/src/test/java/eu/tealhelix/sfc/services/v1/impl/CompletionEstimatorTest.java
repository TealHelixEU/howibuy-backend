package eu.tealhelix.sfc.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The estimated completion time is simply the configured seconds-per-question times the number of questions.
 */
public class CompletionEstimatorTest {
	private final CompletionEstimator estimate = new CompletionEstimator(20);

	@Test
	void anEmptyQuestionSetTakesNoTime() {
		assertEquals(0, estimate.secondsFor(0));
	}

	@Test
	void oneQuestionTakesTheConfiguredSecondsPerQuestion() {
		assertEquals(20, estimate.secondsFor(1));
	}

	@Test
	void theEstimateScalesWithTheQuestionCount() {
		assertEquals(860, estimate.secondsFor(43), "43 questions at 20 seconds each");
	}
}
