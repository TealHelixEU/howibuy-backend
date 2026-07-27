package eu.tealhelix.sfc.jaxrs.v1.exceptionmap;

import java.time.LocalDateTime;

/**
 * The body returned when a new attempt is refused because a prior completed attempt is still within its stability
 * window: a message and the moment the window ends — the earliest a fresh attempt may start.
 */
public record StabilityWindowResponse(String message, LocalDateTime eligibleAt) {
}
