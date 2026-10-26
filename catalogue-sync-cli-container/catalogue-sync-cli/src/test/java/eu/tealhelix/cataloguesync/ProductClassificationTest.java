package eu.tealhelix.cataloguesync;

import static eu.tealhelix.cataloguesync.howibuy.LlmClassificationOutcome.MALFORMED_REPLY;
import static eu.tealhelix.cataloguesync.howibuy.LlmClassificationOutcome.MATCHED;
import static eu.tealhelix.cataloguesync.howibuy.LlmClassificationOutcome.NO_MATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

import eu.tealhelix.cataloguesync.howibuy.ClassificationClient;
import eu.tealhelix.cataloguesync.howibuy.LlmClassificationOutcome;
import eu.tealhelix.cataloguesync.howibuy.LlmClassificationRequest;
import eu.tealhelix.cataloguesync.howibuy.LlmClassificationResponse;
import eu.tealhelix.cataloguesync.howibuy.PendingProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The classification has HowiBuy's LLM classify the pending products a page at a time, in each of their languages, one
 * attempt after the other, until it has attempted as many products as its limit or a page holds none it has not; the
 * attempts that fail are counted and skipped, unless HowiBuy refuses the Catalogue Sync.
 */
@ExtendWith(MockitoExtension.class)
public class ProductClassificationTest {
	private static final int PAGE_SIZE = 5;
	private static final int NO_LIMIT = 100;

	@Mock
	ClassificationClient howiBuy;

	private final FakeTime time = new FakeTime();

	private ProductClassification sut;

	@BeforeEach
	void setUp() {
		sut = new ProductClassification(howiBuy, PAGE_SIZE, time, time::sleep);
	}

	@Test
	void attemptsEachLanguageOfEachPendingProductUntilNoneIsPending() {
		when(howiBuy.findPending(PAGE_SIZE)).thenReturn(List.of(pending("p1", "el", "en"), pending("p2", "el")), List.of());
		when(howiBuy.classify(any())).thenReturn(outcome(MATCHED), outcome(NO_MATCH), outcome(MATCHED));

		var result = sut.classify(NO_LIMIT, OptionalInt.empty());

		var attempts = inOrder(howiBuy);
		attempts.verify(howiBuy).classify(new LlmClassificationRequest("p1", "el"));
		attempts.verify(howiBuy).classify(new LlmClassificationRequest("p1", "en"));
		attempts.verify(howiBuy).classify(new LlmClassificationRequest("p2", "el"));
		assertThat(result).isEqualTo(new ClassificationResult(2, 2, 1, 0, List.of(), Duration.ZERO));
		assertThat(result.attempts()).isEqualTo(3);
	}

	@Test
	void readsPagesOfAsManyProductsAsTheLimitWhenItIsBelowThePageSize() {
		when(howiBuy.findPending(2)).thenReturn(List.of(pending("p1", "el"), pending("p2", "el")));
		when(howiBuy.classify(any())).thenReturn(outcome(MATCHED));

		var result = sut.classify(2, OptionalInt.empty());

		verify(howiBuy, times(1)).findPending(anyInt());
		assertThat(result.products()).isEqualTo(2);
	}

	@Test
	void stopsAtTheLimitOfProductsInTheMiddleOfAPage() {
		when(howiBuy.findPending(2)).thenReturn(List.of(pending("p1", "el")), List.of(pending("p2", "el"), pending("p3", "el")));
		when(howiBuy.classify(any())).thenReturn(outcome(MATCHED));

		var result = sut.classify(2, OptionalInt.empty());

		verify(howiBuy).classify(new LlmClassificationRequest("p2", "el"));
		verify(howiBuy, never()).classify(new LlmClassificationRequest("p3", "el"));
		assertThat(result.products()).isEqualTo(2);
	}

	@Test
	void skipsTheProductsAttemptedInTheRunThatAreStillPendingAndStopsAtAPageOfNoneOther() {
		when(howiBuy.findPending(PAGE_SIZE)).thenReturn(
				List.of(pending("p1", "el"), pending("p2", "el")),
				List.of(pending("p1", "el"), pending("p3", "el")),
				List.of(pending("p1", "el"))
		);
		when(howiBuy.classify(any())).thenReturn(outcome(MALFORMED_REPLY), outcome(MATCHED), outcome(NO_MATCH));

		var result = sut.classify(NO_LIMIT, OptionalInt.empty());

		verify(howiBuy, times(1)).classify(new LlmClassificationRequest("p1", "el"));
		verify(howiBuy).classify(new LlmClassificationRequest("p3", "el"));
		verify(howiBuy, times(3)).findPending(PAGE_SIZE);
		assertThat(result).isEqualTo(new ClassificationResult(3, 1, 1, 1, List.of(), Duration.ZERO));
	}

	@Test
	void countsAndSkipsTheAttemptsThatFail() {
		when(howiBuy.findPending(PAGE_SIZE)).thenReturn(List.of(pending("p1", "el", "en"), pending("p2", "el", "en"), pending("p3", "el")), List.of());
		when(howiBuy.classify(any())).thenThrow(
				new WebApplicationException(400),
				new WebApplicationException(404),
				new WebApplicationException(409),
				new WebApplicationException(500)
		).thenThrow(new ProcessingException("Connection refused")).thenReturn(outcome(MATCHED));

		var result = sut.classify(NO_LIMIT, OptionalInt.empty());

		assertThat(result.failures()).containsExactly(
				new ClassificationFailure("p1", "el", "HTTP status 400"),
				new ClassificationFailure("p1", "en", "HTTP status 404"),
				new ClassificationFailure("p2", "el", "HTTP status 409"),
				new ClassificationFailure("p2", "en", "HTTP status 500"),
				new ClassificationFailure("p3", "el", "Connection refused")
		);
		assertThat(result.products()).isEqualTo(3);
		assertThat(result.attempts()).isEqualTo(5);
	}

	@ParameterizedTest
	@ValueSource(ints = {401, 403})
	void stopsWhenHowiBuyRefusesTheCatalogueSync(int status) {
		var refusal = new WebApplicationException(status);
		when(howiBuy.findPending(PAGE_SIZE)).thenReturn(List.of(pending("p1", "el", "en"), pending("p2", "el")));
		when(howiBuy.classify(any())).thenThrow(refusal);

		assertThatThrownBy(() -> sut.classify(NO_LIMIT, OptionalInt.empty())).isSameAs(refusal);
		verify(howiBuy, times(1)).classify(any());
	}

	@Test
	void stopsWhenThePendingProductsCannotBeRead() {
		var error = new WebApplicationException(503);
		when(howiBuy.findPending(PAGE_SIZE)).thenThrow(error);

		assertThatThrownBy(() -> sut.classify(NO_LIMIT, OptionalInt.empty())).isSameAs(error);
		verify(howiBuy, never()).classify(any());
	}

	@Test
	void spacesTheStartsOfTheAttemptsOutToTheRate() {
		when(howiBuy.findPending(PAGE_SIZE)).thenReturn(List.of(pending("p1", "el", "en"), pending("p2", "el")), List.of());
		when(howiBuy.classify(any()))
				.thenAnswer(_ -> takes(Duration.ofMillis(500), MATCHED))
				.thenAnswer(_ -> takes(Duration.ofSeconds(3), MATCHED))
				.thenAnswer(_ -> takes(Duration.ofMillis(500), MATCHED));

		var result = sut.classify(NO_LIMIT, OptionalInt.of(30));

		assertThat(time.sleeps).containsExactly(Duration.ofMillis(1500));
		assertThat(result.elapsed()).isEqualTo(Duration.ofMillis(5500));
	}

	@Test
	void makesTheAttemptsOneAfterTheOtherWithoutARate() {
		when(howiBuy.findPending(PAGE_SIZE)).thenReturn(List.of(pending("p1", "el", "en")), List.of());
		when(howiBuy.classify(any())).thenAnswer(_ -> takes(Duration.ofMillis(500), MATCHED));

		var result = sut.classify(NO_LIMIT, OptionalInt.empty());

		assertThat(time.sleeps).isEmpty();
		assertThat(result.elapsed()).isEqualTo(Duration.ofSeconds(1));
	}

	@Test
	void stopsWhenInterruptedWhileWaitingForTheNextAttempt() {
		sut = new ProductClassification(howiBuy, PAGE_SIZE, time, _ -> {
			throw new InterruptedException();
		});
		when(howiBuy.findPending(PAGE_SIZE)).thenReturn(List.of(pending("p1", "el", "en")));
		when(howiBuy.classify(any())).thenAnswer(_ -> takes(Duration.ofMillis(500), MATCHED));

		try {
			assertThatThrownBy(() -> sut.classify(NO_LIMIT, OptionalInt.of(30))).isInstanceOf(CancellationException.class);
			verify(howiBuy, times(1)).classify(any());
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		} finally {
			Thread.interrupted();
		}
	}

	private LlmClassificationResponse takes(Duration duration, LlmClassificationOutcome outcome) {
		time.pass(duration);
		return outcome(outcome);
	}

	private static PendingProductResponse pending(String retailerProductId, String... languages) {
		return new PendingProductResponse(retailerProductId, List.of(languages));
	}

	private static LlmClassificationResponse outcome(LlmClassificationOutcome outcome) {
		return new LlmClassificationResponse(outcome);
	}

	private static class FakeTime implements InstantSource {
		private Instant now = Instant.parse("2026-10-26T10:00:00Z");
		private final List<Duration> sleeps = new ArrayList<>();

		@Override
		public Instant instant() {
			return now;
		}

		void pass(Duration duration) {
			now = now.plus(duration);
		}

		void sleep(Duration duration) {
			sleeps.add(duration);
			pass(duration);
		}
	}
}
