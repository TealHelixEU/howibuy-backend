package eu.tealhelix.common.test.time;

import java.time.LocalDateTime;
import java.time.ZoneId;

import eu.tealhelix.common.services.generic.impl.DateTimeServiceImpl;

/**
 * A clock whose "now" the test sets, so that periods of time pass without real time passing. A test installs it with
 * {@code QuarkusMock}, which requires it to be assignable to the real bean class.
 */
public final class TestClock extends DateTimeServiceImpl {
	private volatile LocalDateTime now;

	public TestClock() {
	}

	public TestClock(LocalDateTime now) {
		this.now = now;
	}

	public void set(LocalDateTime now) {
		this.now = now;
	}

	@Override
	public LocalDateTime getNow() {
		return now;
	}

	@Override
	public long currentTimeMillis() {
		return now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}
}
