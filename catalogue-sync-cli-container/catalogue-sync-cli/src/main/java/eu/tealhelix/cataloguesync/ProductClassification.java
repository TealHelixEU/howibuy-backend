package eu.tealhelix.cataloguesync;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import eu.tealhelix.cataloguesync.howibuy.ClassificationClient;
import eu.tealhelix.cataloguesync.howibuy.LlmClassificationOutcome;
import eu.tealhelix.cataloguesync.howibuy.LlmClassificationRequest;
import eu.tealhelix.cataloguesync.howibuy.PendingProductResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Works through the products HowiBuy has waiting to be classified: reads them a page at a time, and has HowiBuy's LLM
 * classify each in every language it is named in, one attempt after the other. HowiBuy records the attempts, so the
 * classification keeps no state, and one that stopped is resumed by running it again.
 */
@Singleton
public class ProductClassification {
	private final ClassificationClient howiBuy;
	private final int pageSize;
	private final InstantSource clock;
	private final Sleep sleep;

	@Inject
	public ProductClassification(
			@RestClient ClassificationClient howiBuy,
			@ConfigProperty(name = "catalogue-sync.classification.page-size") int pageSize
	) {
		this(howiBuy, pageSize, InstantSource.system(), Thread::sleep);
	}

	ProductClassification(ClassificationClient howiBuy, int pageSize, InstantSource clock, Sleep sleep) {
		this.howiBuy = howiBuy;
		this.pageSize = pageSize;
		this.clock = clock;
		this.sleep = sleep;
	}

	/**
	 * A product attempted in this run that is still pending, after a malformed reply or a failure, is not attempted
	 * again, so the classification stops at a page that holds no other. A page holds as many products as the limit, up
	 * to the page size, so while the limit is within the page size those attempted cannot fill a page. An attempt that
	 * fails is counted and skipped; HowiBuy refusing the Catalogue Sync, or any error reading the pending products,
	 * stops the classification.
	 *
	 * @param limit             How many products to attempt at most
	 * @param attemptsPerMinute How many attempts to start in a minute at most; empty to start each one as soon as the
	 *                          one before it ends
	 * @throws CancellationException If the thread is interrupted while waiting to start an attempt
	 */
	public ClassificationResult classify(int limit, OptionalInt attemptsPerMinute) {
		var start = clock.instant();
		var interval = attemptsPerMinute.isPresent() ? Duration.ofMinutes(1).dividedBy(attemptsPerMinute.getAsInt()) : Duration.ZERO;
		var nextAttempt = start;
		var attempted = new HashSet<String>();
		var outcomes = new EnumMap<LlmClassificationOutcome, Integer>(LlmClassificationOutcome.class);
		var failures = new ArrayList<ClassificationFailure>();
		while (attempted.size() < limit) {
			List<PendingProductResponse> products = howiBuy.findPending(Math.min(pageSize, limit)).stream()
					.filter(product -> !attempted.contains(product.retailerProductId()))
					.limit(limit - attempted.size())
					.toList();
			if (products.isEmpty()) {
				break;
			}
			for (var product : products) {
				attempted.add(product.retailerProductId());
				for (var language : product.languages()) {
					nextAttempt = awaitAttempt(nextAttempt, interval);
					try {
						var response = howiBuy.classify(new LlmClassificationRequest(product.retailerProductId(), language));
						outcomes.merge(response.outcome(), 1, Integer::sum);
					} catch (WebApplicationException e) {
						if (refusesTheCatalogueSync(e.getResponse())) {
							throw e;
						}
						failures.add(new ClassificationFailure(product.retailerProductId(), language, "HTTP status " + e.getResponse().getStatus()));
					} catch (ProcessingException e) {
						failures.add(new ClassificationFailure(product.retailerProductId(), language, e.getMessage()));
					}
				}
			}
		}
		return new ClassificationResult(
				attempted.size(),
				outcomes.getOrDefault(LlmClassificationOutcome.MATCHED, 0),
				outcomes.getOrDefault(LlmClassificationOutcome.NO_MATCH, 0),
				outcomes.getOrDefault(LlmClassificationOutcome.MALFORMED_REPLY, 0),
				failures,
				Duration.between(start, clock.instant())
		);
	}

	/**
	 * @return When the attempt after this one may start
	 */
	private Instant awaitAttempt(Instant nextAttempt, Duration interval) {
		var wait = Duration.between(clock.instant(), nextAttempt);
		if (wait.isPositive()) {
			try {
				sleep.sleep(wait);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new CancellationException("The classification was interrupted while waiting for its next attempt");
			}
		}
		return clock.instant().plus(interval);
	}

	private static boolean refusesTheCatalogueSync(Response response) {
		return response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode() || response.getStatus() == Response.Status.FORBIDDEN.getStatusCode();
	}

	/**
	 * Waits before an attempt, so that the attempts keep to their rate.
	 */
	@FunctionalInterface
	interface Sleep {
		void sleep(Duration duration) throws InterruptedException;
	}
}
