package eu.tealhelix.sfc.services.v1.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The configured stability window: how long a completed attempt's answers stay locked before the user may start a fresh
 * attempt (ADR 0003, {@code sfc.stability-window}). Given a completion time it answers when the window ends and whether
 * it has elapsed at a given moment — the eligibility-to-restart comparison, kept free of the clock so it is trivially
 * testable.
 */
@ApplicationScoped
public class StabilityWindow {
	private final Duration duration;

	@Inject
	public StabilityWindow(@ConfigProperty(name = "sfc.stability-window") Duration duration) {
		this.duration = duration;
	}

	/**
	 * When the window that began at {@code completedAt} ends — the earliest a new attempt may start.
	 */
	public LocalDateTime endsAfter(LocalDateTime completedAt) {
		return completedAt.plus(duration);
	}

	/**
	 * Whether the window that began at {@code completedAt} has elapsed by {@code now}. The boundary is inclusive: at the
	 * exact moment the window ends the user is already eligible.
	 */
	public boolean elapsedSince(LocalDateTime completedAt, LocalDateTime now) {
		return !now.isBefore(endsAfter(completedAt));
	}
}
