package eu.tealhelix.catalogue.v1.types;

import static org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress;

import java.util.Objects;

import org.mockito.ArgumentMatcher;

public interface IngestionRunIdTestUtils {
	class MatchesIngestionRunId implements ArgumentMatcher<IngestionRunId> {
		private final IngestionRunId ingestionRunId;

		public MatchesIngestionRunId(IngestionRunId ingestionRunId) {
			this.ingestionRunId = Objects.requireNonNull(ingestionRunId);
		}

		@Override
		public boolean matches(IngestionRunId ingestionRunId) {
			return ingestionRunId != null && Objects.equals(ingestionRunId.asString(), this.ingestionRunId.asString());
		}

		@Override
		public Class<?> type() {
			return IngestionRunId.class;
		}

		@Override
		public String toString() {
			return "IngestionRunId(" + this.ingestionRunId.asString() + ")";
		}
	}

	static IngestionRunId matchesIngestionRunId(IngestionRunId ingestionRunId) {
		reportMatcher(new MatchesIngestionRunId(ingestionRunId));
		return null;
	}

	private static void reportMatcher(ArgumentMatcher<?> matcher) {
		mockingProgress().getArgumentMatcherStorage().reportMatcher(matcher);
	}
}
