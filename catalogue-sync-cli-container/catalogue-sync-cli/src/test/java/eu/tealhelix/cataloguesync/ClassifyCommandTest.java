package eu.tealhelix.cataloguesync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

/**
 * The classify command classifies as many products, at the rate, its options tell, or else its configuration, prints
 * each failed attempt as it fails and the summary of the classification, and exits with a code that tells whether
 * attempts failed.
 */
@ExtendWith(MockitoExtension.class)
public class ClassifyCommandTest {
	private static final ClassificationResult NOTHING = new ClassificationResult(0, 0, 0, 0, 0, Duration.ZERO);

	@Mock
	ProductClassification productClassification;

	private final StringWriter out = new StringWriter();
	private final StringWriter err = new StringWriter();

	private ClassifyCommand command;
	private CommandLine sut;

	@BeforeEach
	void setUp() {
		command = new ClassifyCommand();
		command.productClassification = productClassification;
		command.configuredLimit = 50;
		command.configuredRate = OptionalInt.of(20);
		sut = new CommandLine(command).setOut(new PrintWriter(out)).setErr(new PrintWriter(err));
	}

	@Test
	void classifiesAsConfiguredWithoutOptions() {
		when(productClassification.classify(eq(50), eq(OptionalInt.of(20)), any())).thenReturn(NOTHING);

		assertThat(sut.execute()).isZero();
	}

	@Test
	void classifiesAsTheOptionsTell() {
		when(productClassification.classify(eq(7), eq(OptionalInt.of(3)), any())).thenReturn(NOTHING);

		assertThat(sut.execute("--limit", "7", "--rate", "3")).isZero();
	}

	@Test
	void classifiesUnthrottledWithoutARate() {
		command.configuredRate = OptionalInt.empty();
		when(productClassification.classify(eq(50), eq(OptionalInt.empty()), any())).thenReturn(NOTHING);

		assertThat(sut.execute()).isZero();
	}

	@Test
	void printsEachFailureAsItFailsAndTheSummary() {
		when(productClassification.classify(anyInt(), any(), any())).thenAnswer(invocation -> {
			Consumer<ClassificationFailure> failures = invocation.getArgument(2);
			failures.accept(new ClassificationFailure("p3", "el", "HTTP status 409"));
			failures.accept(new ClassificationFailure("p4", "en", "Connection refused"));
			return new ClassificationResult(4, 2, 1, 1, 2, Duration.ofSeconds(3723));
		});

		sut.execute();

		assertThat(out.toString()).isEqualTo("""
				Failed, retailerProductId: p3, language: el, error: HTTP status 409
				Failed, retailerProductId: p4, language: en, error: Connection refused
				The classification ended, products: 4, attempts: 6, matched: 2, no match: 1, malformed replies: 1, failures: 2, elapsed: 1:02:03
				""");
	}

	@Test
	void exitsWithZeroWhenNoAttemptFailedDespiteMalformedReplies() {
		when(productClassification.classify(anyInt(), any(), any())).thenReturn(new ClassificationResult(3, 1, 1, 1, 0, Duration.ZERO));

		assertThat(sut.execute()).isZero();
	}

	@Test
	void exitsWithItsCodeWhenSomeAttemptsFailed() {
		when(productClassification.classify(anyInt(), any(), any())).thenReturn(new ClassificationResult(2, 1, 0, 0, 1, Duration.ZERO));

		assertThat(sut.execute()).isEqualTo(ClassifyCommand.SOME_ATTEMPTS_FAILED);
	}

	@Test
	void exitsWithItsCodeWhenEveryAttemptFailed() {
		when(productClassification.classify(anyInt(), any(), any())).thenReturn(new ClassificationResult(2, 0, 0, 0, 3, Duration.ZERO));

		assertThat(sut.execute()).isEqualTo(ClassifyCommand.EVERY_ATTEMPT_FAILED);
	}

	@Test
	void exitsWithOneWhenTheClassificationStops() {
		when(productClassification.classify(anyInt(), any(), any())).thenThrow(new IllegalStateException("HowiBuy refused the Catalogue Sync"));

		assertThat(sut.execute()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
	}

	@Test
	void refusesALimitBelowOne() {
		assertThat(sut.execute("--limit", "0")).isEqualTo(CommandLine.ExitCode.USAGE);

		assertThat(err.toString()).startsWith("--limit must be at least 1, limit: 0");
		verify(productClassification, never()).classify(anyInt(), any(), any());
	}

	@Test
	void refusesARateBelowOne() {
		assertThat(sut.execute("--rate", "0")).isEqualTo(CommandLine.ExitCode.USAGE);

		assertThat(err.toString()).startsWith("--rate must be at least 1, rate: 0");
		verify(productClassification, never()).classify(anyInt(), any(), any());
	}
}
