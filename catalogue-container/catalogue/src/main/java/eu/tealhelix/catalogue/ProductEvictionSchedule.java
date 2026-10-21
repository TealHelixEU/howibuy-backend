package eu.tealhelix.catalogue;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.services.v1.impl.ProductEviction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;

/**
 * Runs the eviction at the times of {@code catalogue.eviction.cron}, one run at a time.
 */
@ApplicationScoped
public class ProductEvictionSchedule {
	private final ProductEviction productEviction;

	@Inject
	public ProductEvictionSchedule(ProductEviction productEviction) {
		this.productEviction = productEviction;
	}

	@Scheduled(identity = "product-eviction", cron = "${catalogue.eviction.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
	Uni<Void> evict() {
		return productEviction.evict().replaceWithVoid();
	}
}
