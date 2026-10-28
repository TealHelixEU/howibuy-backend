package eu.tealhelix.catalogue.tests;

import static eu.tealhelix.catalogue.tests.TestFeeds.awaitTheEndOf;
import static eu.tealhelix.catalogue.tests.TestFeeds.deletion;
import static eu.tealhelix.catalogue.tests.TestFeeds.feed;
import static eu.tealhelix.catalogue.tests.TestFeeds.product;
import static eu.tealhelix.catalogue.tests.TestProductChanges.changeOf;
import static eu.tealhelix.catalogue.tests.TestProductChanges.changesAfter;
import static eu.tealhelix.catalogue.tests.TestProductChanges.endOfTheChanges;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.services.v1.FeedService;
import eu.tealhelix.catalogue.services.v1.impl.ProductEviction;
import eu.tealhelix.catalogue.services.v1.impl.UniAwaiter;
import eu.tealhelix.catalogue.test.quarkus.WithCatalogueDb;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.test.time.TestClock;
import eu.tealhelix.common.v1.model.Role;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * How the eviction forgets the products that the Retailer stopped selling long ago, in the database: a Listing inactive
 * for longer than the listing threshold (90 days) is deleted, a product left without Listings becomes a tombstone, which
 * the Catalogue Sync reads as a change, and a tombstone is purged after the purge period (365 days). A tombstone that a
 * Feed carries again comes back with its id. The clock is the test's, so that the days pass at once. Lime Fresh pushes the
 * Feeds through the service to a {@link TestCatalogue} of each test's own; the changes are read as the Catalogue Sync.
 * The eviction runs over the whole database, so that it also evicts what other tests left behind long ago, which is
 * nothing these tests examine.
 */
@QuarkusTest
@WithCatalogueDb
@TestSecurity(user = "catalogue-sync", roles = "catalogue-sync")
@OidcSecurity(claims = {@Claim(key = "sub", value = ProductChangesTest.CATALOGUE_SYNC), @Claim(key = "client_id", value = "catalogue-sync")})
class ProductEvictionTest {
	private static final User LIME_FRESH = new UserImpl(new UserIdImpl(TestCatalogue.LIME_FRESH), null, null, false, false, Set.of(Role.RETAILER));

	@Inject
	DataSource dataSource;

	@Inject
	FeedService feedService;

	@Inject
	ProductEviction productEviction;

	@Inject
	UniAwaiter uniAwaiter;

	private LocalDateTime start;

	private TestClock clock;

	private TestCatalogue catalogue;

	@BeforeEach
	void createTheCatalogueOfTheTest() throws SQLException {
		start = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
		clock = new TestClock(start);
		QuarkusMock.installMockForType(clock, DateTimeService.class);
		catalogue = TestCatalogue.create(dataSource);
	}

	@AfterEach
	void removeTheCatalogueOfTheTest() throws SQLException {
		catalogue.remove();
	}

	@Test
	void keepsAnInactiveListingUpToTheListingThreshold() throws Exception {
		takeMilkOffSale();

		evictAfterDays(89);

		assertThat(catalogue.listing(catalogue.key("milk")).active()).isFalse();
		assertThat(catalogue.names(catalogue.key("milk"))).isEqualTo(Map.of("el", "Γάλα"));
	}

	@Test
	void aProductLeftWithoutListingsBecomesATombstoneThatTheCatalogueSyncReads() throws Exception {
		takeMilkOffSale();
		var after = endOfTheChanges();

		evictAfterDays(91);

		var milk = changeOf(after, catalogue.key("milk"));
		assertThat(milk).containsEntry("removed", true);
		assertThat(changeOf(after, catalogue.key("bread"))).isNull();
		assertThat(catalogue.names(catalogue.key("milk"))).isEmpty();
		assertThat(catalogue.listing(catalogue.key("bread")).active()).isTrue();
	}

	@Test
	void evictingAgainChangesNothing() throws Exception {
		takeMilkOffSale();
		evictAfterDays(91);
		var stamps = catalogue.changeSequences();
		var after = endOfTheChanges();

		evictAfterDays(92);

		assertThat(catalogue.changeSequences()).isEqualTo(stamps);
		assertThat(changesAfter(after, 10).getList("products")).isEmpty();
	}

	@Test
	void aTombstoneThatAFeedCarriesAgainComesBackWithItsId() throws Exception {
		takeMilkOffSale();
		var id = catalogue.productId(catalogue.key("milk"));
		evictAfterDays(91);
		var after = endOfTheChanges();
		clock.set(start.plusDays(100));

		process("DELTA", feed(product(catalogue.key("milk"), Map.of("el", "Γάλα", "en", "Milk"), "1.19")));

		var milk = changeOf(after, catalogue.key("milk"));
		assertThat(milk).containsEntry("removed", false).containsEntry("names", Map.of("el", "Γάλα", "en", "Milk"));
		assertThat(milk.get("listings")).isEqualTo(List.of(
				Map.of("catalogueId", catalogue.id(), "price", new BigDecimal("1.1900"), "currency", "EUR", "active", true)
		));
		assertThat(catalogue.productId(catalogue.key("milk"))).isEqualTo(id);
	}

	@Test
	void purgesATombstoneAfterThePurgePeriod() throws Exception {
		takeMilkOffSale();
		evictAfterDays(91);

		evictAfterDays(91 + 364);
		assertThat(catalogue.changeSequences()).containsKey(catalogue.key("milk"));

		evictAfterDays(91 + 366);
		assertThat(catalogue.changeSequences()).containsOnlyKeys(catalogue.key("bread"));
	}

	/**
	 * Push milk and bread at the start of the test, then take milk off sale with a Deletion Feed; milk was last seen at
	 * the start.
	 */
	private void takeMilkOffSale() throws SQLException, InterruptedException {
		process("SNAPSHOT", feed(product(catalogue.key("milk"), Map.of("el", "Γάλα"), "1.29"), product(catalogue.key("bread"), Map.of("el", "Ψωμί"), "0.99")));
		process("DELETION", feed(deletion(catalogue.key("milk"))));
	}

	private void evictAfterDays(int days) {
		clock.set(start.plusDays(days));
		uniAwaiter.await(productEviction::evict);
	}

	/**
	 * Push a Feed as Lime Fresh and wait until its run is done.
	 */
	private void process(String feedType, String feed) throws SQLException, InterruptedException {
		var received = feedService.receive(LIME_FRESH, new CatalogueIdImpl(catalogue.id()), FeedType.valueOf(feedType), new ByteArrayInputStream(feed.getBytes(UTF_8)));
		assertThat(awaitTheEndOf(dataSource, received.runs().getFirst().getId().asString())).isEqualTo("DONE");
	}
}
