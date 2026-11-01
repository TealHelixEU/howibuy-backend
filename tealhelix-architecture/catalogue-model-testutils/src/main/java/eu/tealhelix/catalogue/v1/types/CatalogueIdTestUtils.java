package eu.tealhelix.catalogue.v1.types;

import static org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress;

import java.util.Objects;

import org.mockito.ArgumentMatcher;

public interface CatalogueIdTestUtils {
	final class MatchesHasCatalogueId implements ArgumentMatcher<HasCatalogueId> {
		private final CatalogueId catalogueId;

		public MatchesHasCatalogueId(HasCatalogueId hasCatalogueId) {
			this.catalogueId = Objects.requireNonNull(Objects.requireNonNull(hasCatalogueId).getId(), "hasCatalogueId.getId() must not be null");
		}

		@Override
		public boolean matches(HasCatalogueId hasCatalogueId) {
			return hasCatalogueId != null && hasCatalogueId.getId() != null && Objects.equals(hasCatalogueId.getId().asString(), this.catalogueId.asString());
		}

		@Override
		public Class<?> type() {
			return HasCatalogueId.class;
		}

		@Override
		public String toString() {
			return "HasCatalogueId(" + this.catalogueId.asString() + ")";
		}
	}

	static HasCatalogueId matchesHasCatalogueId(HasCatalogueId hasCatalogueId) {
		reportMatcher(new MatchesHasCatalogueId(hasCatalogueId));
		return null;
	}

	private static void reportMatcher(ArgumentMatcher<?> matcher) {
		mockingProgress().getArgumentMatcherStorage().reportMatcher(matcher);
	}
}
