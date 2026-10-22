package eu.tealhelix.howibuy.v1.types;

import static org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress;

import java.util.Objects;

import org.mockito.ArgumentMatcher;

public interface RetailerProductIdTestUtils {
	class MatchesHasRetailerProductId implements ArgumentMatcher<HasRetailerProductId> {
		private final RetailerProductId retailerProductId;

		public MatchesHasRetailerProductId(HasRetailerProductId hasRetailerProductId) {
			this.retailerProductId = Objects.requireNonNull(Objects.requireNonNull(hasRetailerProductId).getId(), "hasRetailerProductId.getId() must not be null");
		}

		@Override
		public boolean matches(HasRetailerProductId hasRetailerProductId) {
			return hasRetailerProductId != null && hasRetailerProductId.getId() != null && Objects.equals(hasRetailerProductId.getId().asString(), this.retailerProductId.asString());
		}

		@Override
		public Class<?> type() {
			return HasRetailerProductId.class;
		}

		@Override
		public String toString() {
			return "HasRetailerProductId(" + this.retailerProductId.asString() + ")";
		}
	}

	static HasRetailerProductId matchesHasRetailerProductId(HasRetailerProductId hasRetailerProductId) {
		reportMatcher(new MatchesHasRetailerProductId(hasRetailerProductId));
		return null;
	}

	private static void reportMatcher(ArgumentMatcher<?> matcher) {
		mockingProgress().getArgumentMatcherStorage().reportMatcher(matcher);
	}
}
