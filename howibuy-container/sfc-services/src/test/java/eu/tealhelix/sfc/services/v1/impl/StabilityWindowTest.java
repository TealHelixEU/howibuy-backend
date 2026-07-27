package eu.tealhelix.sfc.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * The stability-window comparison in isolation: when a window that began at a completion time ends, and whether it has
 * elapsed at a given moment. The boundary is inclusive — at the exact moment the window ends the user is already
 * eligible. Kept free of the clock so eligibility is trivially testable (ADR 0003).
 */
public class StabilityWindowTest {
	private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

	private final StabilityWindow sut = new StabilityWindow(Duration.ofDays(30));

	@Test
	void endsAfterAddsTheWindowToTheCompletionTime() {
		assertEquals(LocalDateTime.of(2026, 1, 31, 12, 0, 0), sut.endsAfter(COMPLETED_AT), "30 days after completion");
	}

	@Test
	void notElapsedBeforeTheWindowEnds() {
		assertFalse(sut.elapsedSince(COMPLETED_AT, COMPLETED_AT.plusDays(30).minusSeconds(1)), "one second short of the window");
	}

	@Test
	void elapsedExactlyAtTheBoundary() {
		assertTrue(sut.elapsedSince(COMPLETED_AT, COMPLETED_AT.plusDays(30)), "the boundary is inclusive — eligible at the exact end");
	}

	@Test
	void elapsedAfterTheWindowEnds() {
		assertTrue(sut.elapsedSince(COMPLETED_AT, COMPLETED_AT.plusDays(30).plusSeconds(1)), "one second past the window");
	}
}
