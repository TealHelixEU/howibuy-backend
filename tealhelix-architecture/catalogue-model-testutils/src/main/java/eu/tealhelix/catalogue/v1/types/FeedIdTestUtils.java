package eu.tealhelix.catalogue.v1.types;

import static org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress;

import java.util.Objects;

import org.mockito.ArgumentMatcher;

public interface FeedIdTestUtils {
	final class MatchesHasFeedId implements ArgumentMatcher<HasFeedId> {
		private final FeedId feedId;

		public MatchesHasFeedId(HasFeedId hasFeedId) {
			this.feedId = Objects.requireNonNull(Objects.requireNonNull(hasFeedId).getId(), "hasFeedId.getId() must not be null");
		}

		@Override
		public boolean matches(HasFeedId hasFeedId) {
			return hasFeedId != null && hasFeedId.getId() != null && Objects.equals(hasFeedId.getId().asString(), this.feedId.asString());
		}

		@Override
		public Class<?> type() {
			return HasFeedId.class;
		}

		@Override
		public String toString() {
			return "HasFeedId(" + this.feedId.asString() + ")";
		}
	}

	static HasFeedId matchesHasFeedId(HasFeedId hasFeedId) {
		reportMatcher(new MatchesHasFeedId(hasFeedId));
		return null;
	}

	private static void reportMatcher(ArgumentMatcher<?> matcher) {
		mockingProgress().getArgumentMatcherStorage().reportMatcher(matcher);
	}
}
