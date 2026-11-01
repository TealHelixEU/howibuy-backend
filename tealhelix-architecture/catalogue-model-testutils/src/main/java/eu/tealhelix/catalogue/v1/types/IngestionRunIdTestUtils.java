package eu.tealhelix.catalogue.v1.types;

import static org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress;

import java.util.Objects;

import org.mockito.ArgumentMatcher;

public interface IngestionRunIdTestUtils {
	final class MatchesHasIngestionRunId implements ArgumentMatcher<HasIngestionRunId> {
		private final IngestionRunId ingestionRunId;

		public MatchesHasIngestionRunId(HasIngestionRunId hasIngestionRunId) {
			this.ingestionRunId = Objects.requireNonNull(Objects.requireNonNull(hasIngestionRunId).getId(), "hasIngestionRunId.getId() must not be null");
		}

		@Override
		public boolean matches(HasIngestionRunId hasIngestionRunId) {
			return hasIngestionRunId != null && hasIngestionRunId.getId() != null && Objects.equals(hasIngestionRunId.getId().asString(), this.ingestionRunId.asString());
		}

		@Override
		public Class<?> type() {
			return HasIngestionRunId.class;
		}

		@Override
		public String toString() {
			return "HasIngestionRunId(" + this.ingestionRunId.asString() + ")";
		}
	}

	static HasIngestionRunId matchesHasIngestionRunId(HasIngestionRunId hasIngestionRunId) {
		reportMatcher(new MatchesHasIngestionRunId(hasIngestionRunId));
		return null;
	}

	private static void reportMatcher(ArgumentMatcher<?> matcher) {
		mockingProgress().getArgumentMatcherStorage().reportMatcher(matcher);
	}
}
