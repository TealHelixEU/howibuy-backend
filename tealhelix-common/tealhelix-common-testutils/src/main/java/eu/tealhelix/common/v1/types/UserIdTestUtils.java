package eu.tealhelix.common.v1.types;

import static org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress;

import java.util.Objects;

import org.mockito.ArgumentMatcher;

public interface UserIdTestUtils {
	class MatchesHasUserIdWithHasUserId implements ArgumentMatcher<HasUserId> {
		private final HasUserId hasUserId;

		public MatchesHasUserIdWithHasUserId(HasUserId hasUserId) {
			this.hasUserId = Objects.requireNonNull(hasUserId);
			Objects.requireNonNull(hasUserId.getId(), "hasUserId.getId() must not be null");
		}

		@Override
		public boolean matches(HasUserId hasUserId) {
			return hasUserId != null && hasUserId.getId() != null
					&& hasUserId.getId().equals(this.hasUserId.getId());
		}

		@Override
		public Class<?> type() {
			return HasUserId.class;
		}

		@Override
		public String toString() {
			return "HasUserId(hasUserId.getId()=" + this.hasUserId.getId() + ")";
		}
	}

	static HasUserId matchesHasUserId(HasUserId hasUserId) {
		reportMatcher(new MatchesHasUserIdWithHasUserId(hasUserId));
		return null;
	}

	private static void reportMatcher(ArgumentMatcher<?> matcher) {
		mockingProgress().getArgumentMatcherStorage().reportMatcher(matcher);
	}
}
