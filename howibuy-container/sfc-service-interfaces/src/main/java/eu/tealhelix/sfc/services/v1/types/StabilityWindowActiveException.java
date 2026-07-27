package eu.tealhelix.sfc.services.v1.types;

import java.time.LocalDateTime;

/**
 * Thrown when a user with no in-progress attempt tries to answer while a previously completed attempt is still inside
 * its stability window. Carries the moment the window ends — the earliest a fresh attempt may start (mapped to HTTP
 * 409).
 */
public class StabilityWindowActiveException extends RuntimeException {
	private final LocalDateTime eligibleAt;

	public StabilityWindowActiveException(LocalDateTime eligibleAt) {
		super("Cannot start a new compass attempt until the stability window elapses, eligible at: " + eligibleAt);
		this.eligibleAt = eligibleAt;
	}

	public LocalDateTime getEligibleAt() {
		return eligibleAt;
	}
}
