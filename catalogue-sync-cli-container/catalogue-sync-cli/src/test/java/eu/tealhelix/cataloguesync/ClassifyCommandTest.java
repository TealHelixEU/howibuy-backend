package eu.tealhelix.cataloguesync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

/**
 * The classify command classifies as many products, at the rate, its options tell, or else its configuration, and
 * prints the summary of the classification.
 */
@ExtendWith(MockitoExtension.class)
public class ClassifyCommandTest {
	private static final ClassificationResult NOTHING = new ClassificationResult(0, 0, 0, 0, List.of(), Duration.ZERO);

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
		when(productClassification.classify(50, OptionalInt.of(20))).thenReturn(NOTHING);

		assertThat(sut.execute()).isZero();
	}

	@Test
	void classifiesAsTheOptionsTell() {
		when(productClassification.classify(7, OptionalInt.of(3))).thenReturn(NOTHING);

		assertThat(sut.execute("--limit", "7", "--rate", "3")).isZero();
	}

	@Test
	void classifiesUnthrottledWithoutARate() {
		command.configuredRate = OptionalInt.empty();
		when(productClassification.classify(50, OptionalInt.empty())).thenReturn(NOTHING);

		assertThat(sut.execute()).isZero();
	}

	@Test
	void printsTheSummaryAndEachFailure() {
		when(productClassification.classify(anyInt(), any())).thenReturn(new ClassificationResult(
				4, 2, 1, 1,
				List.of(new ClassificationFailure("p3", "el", "HTTP status 409"), new ClassificationFailure("p4", "en", "Connection refused")),
				Duration.ofSeconds(3723)
		));

		sut.execute();

		assertThat(out.toString()).isEqualTo("""
				The classification ended, products: 4, attempts: 6, matched: 2, no match: 1, malformed replies: 1, failures: 2, elapsed: 1:02:03
				Failed, retailerProductId: p3, language: el, error: HTTP status 409
				Failed, retailerProductId: p4, language: en, error: Connection refused
				""");
	}

	@Test
	void refusesALimitBelowOne() {
		assertThat(sut.execute("--limit", "0")).isEqualTo(CommandLine.ExitCode.USAGE);

		assertThat(err.toString()).startsWith("--limit must be at least 1, limit: 0");
		verify(productClassification, never()).classify(anyInt(), any());
	}

	@Test
	void refusesARateBelowOne() {
		assertThat(sut.execute("--rate", "0")).isEqualTo(CommandLine.ExitCode.USAGE);

		assertThat(err.toString()).startsWith("--rate must be at least 1, rate: 0");
		verify(productClassification, never()).classify(anyInt(), any());
	}
}
