package eu.tealhelix.common.services.jee.impl;

import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;

/**
 * Shared circuit breaker around synchronous AI service calls. All AI facades route their calls through
 * {@link #guard(Supplier)} so that repeated failures (e.g. Ollama timeouts) open a single, shared breaker and
 * subsequent calls fail fast with {@link org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException}
 * instead of tying up worker threads.
 */
@ApplicationScoped
public class AiCircuitBreaker {
	@CircuitBreaker(
			requestVolumeThreshold = 4,
			failureRatio = 0.5,
			delay = 30,
			delayUnit = ChronoUnit.SECONDS,
			successThreshold = 1
	)
	public <T> T guard(Supplier<T> call) {
		return call.get();
	}
}
