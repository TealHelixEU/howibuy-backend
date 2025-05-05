package eu.tealhelix.common.services.generic.impl;

import java.time.LocalDateTime;

import eu.tealhelix.common.services.generic.DateTimeService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Implementation of {@link DateTimeService}.
 */
@ApplicationScoped
class DateTimeServiceImpl implements DateTimeService {

	@Override
	public LocalDateTime getNow() {
		return LocalDateTime.now();
	}

	@Override
	public long currentTimeMillis() {
		return System.currentTimeMillis();
	}
}
