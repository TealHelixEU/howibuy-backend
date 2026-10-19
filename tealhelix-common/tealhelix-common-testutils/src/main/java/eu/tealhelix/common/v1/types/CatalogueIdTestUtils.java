package eu.tealhelix.common.v1.types;

import static org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress;

import java.util.Objects;

import org.mockito.ArgumentMatcher;

public interface CatalogueIdTestUtils {
	class MatchesCatalogueId implements ArgumentMatcher<CatalogueId> {
		private final CatalogueId catalogueId;

		public MatchesCatalogueId(CatalogueId catalogueId) {
			this.catalogueId = Objects.requireNonNull(catalogueId);
		}

		@Override
		public boolean matches(CatalogueId catalogueId) {
			return catalogueId != null && Objects.equals(catalogueId.asString(), this.catalogueId.asString());
		}

		@Override
		public Class<?> type() {
			return CatalogueId.class;
		}

		@Override
		public String toString() {
			return "CatalogueId(" + this.catalogueId.asString() + ")";
		}
	}

	static CatalogueId matchesCatalogueId(CatalogueId catalogueId) {
		reportMatcher(new MatchesCatalogueId(catalogueId));
		return null;
	}

	private static void reportMatcher(ArgumentMatcher<?> matcher) {
		mockingProgress().getArgumentMatcherStorage().reportMatcher(matcher);
	}
}
