package eu.tealhelix.howibuy.v1.types;

import static org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress;

import java.util.Objects;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.UserId;
import org.mockito.ArgumentMatcher;

public interface RetailerIdTestUtils {
	class MatchesRetailerIdWithUserId implements ArgumentMatcher<RetailerId> {
		private final UserId userId;

		public MatchesRetailerIdWithUserId(UserId userId) {
			this.userId = Objects.requireNonNull(userId);
		}

		@Override
		public boolean matches(RetailerId retailerId) {
			return retailerId != null && retailerId.asUuid() != null && retailerId.asUuid().equals(userId.asUuid());
		}

		@Override
		public Class<?> type() {
			return RetailerId.class;
		}

		@Override
		public String toString() {
			return "RetailerId(from UserId=" + userId.asString() + ")";
		}
	}

	class MatchesRetailerIdWithUser implements ArgumentMatcher<RetailerId> {
		private final User user;

		public MatchesRetailerIdWithUser(User user) {
			this.user = Objects.requireNonNull(user);
		}

		@Override
		public boolean matches(RetailerId retailerId) {
			return retailerId != null && retailerId.asUuid() != null && retailerId.asUuid().equals(user.getId().asUuid());
		}

		@Override
		public Class<?> type() {
			return RetailerId.class;
		}

		@Override
		public String toString() {
			return "RetailerId(from User=" + user + ")";
		}
	}

	class MatchesHasRetailerIdWithUser implements ArgumentMatcher<HasRetailerId> {
		private final User user;

		public MatchesHasRetailerIdWithUser(User user) {
			this.user = Objects.requireNonNull(user);
		}

		@Override
		public boolean matches(HasRetailerId hasRetailerId) {
			return hasRetailerId != null && hasRetailerId.getId() != null && hasRetailerId.getId().asUuid() != null
					&& hasRetailerId.getId().asUuid().equals(user.getId().asUuid());
		}

		@Override
		public Class<?> type() {
			return HasRetailerId.class;
		}

		@Override
		public String toString() {
			return "HasRetailerId(from User=" + user + ")";
		}
	}

	static RetailerId matchesRetailerId(UserId retailerUserId) {
		reportMatcher(new MatchesRetailerIdWithUserId(retailerUserId));
		return null;
	}

	static RetailerId matchesRetailerId(User retailerUser) {
		reportMatcher(new MatchesRetailerIdWithUser(retailerUser));
		return null;
	}

	static HasRetailerId hasRetailerId(User retailerUser) {
		reportMatcher(new MatchesHasRetailerIdWithUser(retailerUser));
		return null;
	}

	private static void reportMatcher(ArgumentMatcher<?> matcher) {
		mockingProgress().getArgumentMatcherStorage().reportMatcher(matcher);
	}
}
