package eu.tealhelix.sfc.jaxrs.v1.exceptionmap;

import java.time.LocalDateTime;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * The body returned when a new attempt is refused because a prior completed attempt is still within its stability
 * window: a message and the moment the window ends — the earliest a fresh attempt may start.
 */
@Schema(description = "Why a fresh attempt was refused: a completed one is still inside its stability window.")
public record StabilityWindowResponse(
		@Schema(description = "What was refused and why.")
		String message,
		@Schema(description = "When the window ends — the earliest a fresh attempt may start.")
		LocalDateTime eligibleAt
) {
}
