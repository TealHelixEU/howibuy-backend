package eu.tealhelix.sfc.services.v1.types;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Progress carries the raw answered/total counts and a percentage rounded to the nearest whole number, with an empty
 * question set reported as 0% rather than dividing by zero.
 */
public class ProgressTest {
	@Test
	void noQuestionsIsZeroPercentRatherThanDivideByZero() {
		assertEquals(new Progress(0, 0, 0), Progress.of(0, 0));
	}

	@Test
	void nothingAnsweredIsZeroPercent() {
		assertEquals(new Progress(0, 43, 0), Progress.of(0, 43));
	}

	@Test
	void everythingAnsweredIsAHundredPercent() {
		assertEquals(new Progress(43, 43, 100), Progress.of(43, 43));
	}

	@Test
	void thePercentageIsRoundedToTheNearestWholeNumber() {
		assertEquals(33, Progress.of(1, 3).percentage(), "33.3% rounds down to 33");
		assertEquals(67, Progress.of(2, 3).percentage(), "66.7% rounds up to 67");
	}
}
