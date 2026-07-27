package eu.tealhelix.common.services.generic.impl;

import java.time.LocalDateTime;

import eu.tealhelix.common.services.generic.DateTimeService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Implementation of {@link DateTimeService}. Public and non-final so tests can extend it to stand in a controllable
 * clock via {@code QuarkusMock} (which requires the stub be assignable to the real bean class).
 */
@ApplicationScoped
public class DateTimeServiceImpl implements DateTimeService {

	@Override
	public LocalDateTime getNow() {
		return LocalDateTime.now();
	}

	@Override
	public long currentTimeMillis() {
		return System.currentTimeMillis();
	}
}
