package eu.tealhelix.catalogue.dao.impl;

import static eu.tealhelix.catalogue.dao.impl.TestEntities.catalogue;
import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import eu.tealhelix.catalogue.dao.jpa.IngestionRunEntity;
import eu.tealhelix.catalogue.v1.model.ImmutableListing;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.Listing;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public class RetailerProductDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final RetailerId RETAILER = new GenericRetailerId("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11");
	private static final RetailerId OTHER_RETAILER = new GenericRetailerId("0b7e2d4c-1a3f-4e5b-8c6d-9f0a1b2c3d4e");
	private static final CatalogueId CATALOGUE = new CatalogueIdImpl("test-lt");
	private static final CatalogueId OTHER_CATALOGUE = new CatalogueIdImpl("test-lv");
	private static final CatalogueId OTHER_RETAILERS_CATALOGUE = new CatalogueIdImpl("other-lt");
	private static final IngestionRunId RUN = new IngestionRunIdImpl("5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e01");
	private static final IngestionRunId LATER_RUN = new IngestionRunIdImpl("5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e02");
	private static final IngestionRunId OTHER_CATALOGUE_RUN = new IngestionRunIdImpl("5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e03");
	private static final IngestionRunId OTHER_RETAILERS_RUN = new IngestionRunIdImpl("5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e04");
	private static final LocalDateTime SEEN_AT = LocalDateTime.of(2026, 10, 21, 10, 0, 0);
	private static final LocalDateTime LATER = LocalDateTime.of(2026, 10, 22, 10, 0, 0);
	/**
	 * The tests that evict, make tombstones and purge choose times before {@link #SEEN_AT}, each apart from the others',
	 * so that the rows they leave behind are never theirs to evict or purge.
	 */
	private static final LocalDateTime LONG_AGO = LocalDateTime.of(2025, 1, 10, 10, 0, 0);
	private static final LocalDateTime REMOVED_AT = LocalDateTime.of(2025, 6, 1, 3, 30, 0);
	private static final LocalDateTime PURGED_REMOVED_AT = LocalDateTime.of(2019, 3, 1, 3, 30, 0);
	private static final Currency EUR = Currency.getInstance("EUR");
	/**
	 * Above the values of the change sequence that the tests give their products by hand.
	 */
	private static final long FIRST_TAKEN_CHANGE_SEQUENCE = 1000;

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "catalogue.db.changelog.xml", "test");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	/**
	 * The Retailers are inserted with plain JDBC, not JPA: {@code IN_RETAILER} has no entity. The change sequence is
	 * advanced past the values that the tests give their products by hand, so that the values it gives follow them.
	 */
	@BeforeAll
	static void setUp(Mutiny.SessionFactory sessionFactory) throws SQLException {
		execute("INSERT INTO IN_RETAILER (id, name) VALUES ('" + RETAILER.asString() + "', 'Test Retailer'), ('" + OTHER_RETAILER.asString() + "', 'Other Retailer')");
		execute("SELECT setval('IN_CHANGE_SEQUENCE', " + FIRST_TAKEN_CHANGE_SEQUENCE + ", FALSE)");
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> tx.persistAll(
								catalogue(CATALOGUE, RETAILER, "LT", "lt", "en"),
								catalogue(OTHER_CATALOGUE, RETAILER, "LV", "lv", "en"),
								catalogue(OTHER_RETAILERS_CATALOGUE, OTHER_RETAILER, "LT", "lt"))
						.chain(() -> tx.persistAll(
								ingestionRun(RUN, CATALOGUE),
								ingestionRun(LATER_RUN, CATALOGUE),
								ingestionRun(OTHER_CATALOGUE_RUN, OTHER_CATALOGUE),
								ingestionRun(OTHER_RETAILERS_RUN, OTHER_RETAILERS_CATALOGUE))))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	@Test
	void readsBackASavedProduct(Mutiny.SessionFactory sessionFactory) {
		var product = product(RETAILER, newKey(), 7, listing(CATALOGUE, "1.29", true));

		save(sessionFactory, CATALOGUE, RUN, SEEN_AT, product);

		assertEquals(Map.of(product.getProductKey(), product), findProducts(sessionFactory, RETAILER, product.getProductKey()));
	}

	@Test
	void replacesTheStateOfASavedProduct(Mutiny.SessionFactory sessionFactory) {
		var key = newKey();
		save(sessionFactory, CATALOGUE, RUN, SEEN_AT, product(RETAILER, key, 7, listing(CATALOGUE, "1.29", true)));
		var changed = ImmutableProductChange.builder()
				.from(product(RETAILER, key, 8, listing(CATALOGUE, "1.19", false), listing(OTHER_CATALOGUE, "1.49", true)))
				.names(Map.of("lt", "Pienas 2.5%", "lv", "Piens"))
				.characteristics(Map.of("volume", "1l"))
				.tags(List.of("organic"))
				.inputHash("b".repeat(64))
				.isRemoved(true)
				.build();

		save(sessionFactory, OTHER_CATALOGUE, OTHER_CATALOGUE_RUN, LATER, changed);

		assertEquals(Map.of(key, changed), findProducts(sessionFactory, RETAILER, key));
	}

	/**
	 * More products than the batches of the Ingestion hold.
	 */
	@Test
	void savesManyNewProductsAtOnce(Mutiny.SessionFactory sessionFactory) {
		var products = Stream.generate(() -> product(RETAILER, newKey(), 7, listing(CATALOGUE, "1.29", true))).limit(2000).toList();

		save(sessionFactory, CATALOGUE, RUN, SEEN_AT, products.toArray(ProductChange[]::new));

		var keys = products.stream().map(ProductChange::getProductKey).toArray(ProductKey[]::new);
		assertEquals(products.stream().collect(Collectors.toMap(ProductChange::getProductKey, Function.identity())), findProducts(sessionFactory, RETAILER, keys));
	}

	@Test
	void findsOnlyTheProductsAskedForOfTheRetailer(Mutiny.SessionFactory sessionFactory) {
		var key = newKey();
		var otherKey = newKey();
		var product = product(RETAILER, key, 7, listing(CATALOGUE, "1.29", true));
		save(sessionFactory, CATALOGUE, RUN, SEEN_AT, product, product(RETAILER, otherKey, 7, listing(CATALOGUE, "1.29", true)));
		save(sessionFactory, OTHER_RETAILERS_CATALOGUE, OTHER_RETAILERS_RUN, SEEN_AT, product(OTHER_RETAILER, key, 7, listing(OTHER_RETAILERS_CATALOGUE, "1.29", true)));

		assertEquals(Map.of(key, product), findProducts(sessionFactory, RETAILER, key, newKey()));
	}

	@Test
	void findsNoProductsForNoKeys(Mutiny.SessionFactory sessionFactory) {
		assertEquals(Map.of(), findProducts(sessionFactory, RETAILER));
	}

	@Test
	void recordsTheListingsOfTheCatalogueAsSeenByTheRun(Mutiny.SessionFactory sessionFactory) throws SQLException {
		var key = newKey();
		save(sessionFactory, CATALOGUE, RUN, SEEN_AT, product(RETAILER, key, 7, listing(CATALOGUE, "1.29", true)));
		save(sessionFactory, OTHER_CATALOGUE, OTHER_CATALOGUE_RUN, SEEN_AT, product(RETAILER, key, 8, listing(CATALOGUE, "1.29", true), listing(OTHER_CATALOGUE, "1.49", true)));

		save(sessionFactory, CATALOGUE, LATER_RUN, LATER, product(RETAILER, key, 8, listing(CATALOGUE, "1.29", true), listing(OTHER_CATALOGUE, "1.49", true)));

		assertEquals(
				List.of(
						List.of(CATALOGUE.asString(), LATER.toString(), LATER_RUN.asString()),
						List.of(OTHER_CATALOGUE.asString(), SEEN_AT.toString(), OTHER_CATALOGUE_RUN.asString())
				),
				findSeen(key)
		);
	}

	@Test
	void countsTheActiveListingsOfACatalogueAndThoseARunDidNotSee(Mutiny.SessionFactory sessionFactory) {
		var catalogue = createCatalogueWithRuns(sessionFactory);
		var seen = newKey();
		save(sessionFactory, catalogue.id(), catalogue.run(), SEEN_AT,
				product(RETAILER, seen, 7, listing(catalogue.id(), "1.29", true)),
				product(RETAILER, newKey(), 7, listing(catalogue.id(), "1.29", true)),
				product(RETAILER, newKey(), 7, listing(catalogue.id(), "1.29", false)));
		save(sessionFactory, catalogue.id(), catalogue.laterRun(), LATER, product(RETAILER, seen, 7, listing(catalogue.id(), "1.29", true)));

		assertEquals(2, countActiveListings(sessionFactory, catalogue.id()));
		assertEquals(1, countUnseenListings(sessionFactory, catalogue.id(), catalogue.laterRun()));
	}

	@Test
	void deactivatesTheActiveListingsARunDidNotSeeAndStampsTheirProducts(Mutiny.SessionFactory sessionFactory) throws SQLException {
		var catalogue = createCatalogueWithRuns(sessionFactory);
		var seen = product(RETAILER, newKey(), 7, listing(catalogue.id(), "1.29", true));
		var unseen = product(RETAILER, newKey(), 7, listing(catalogue.id(), "0.99", true), listing(OTHER_CATALOGUE, "1.09", true));
		var inactive = product(RETAILER, newKey(), 7, listing(catalogue.id(), "4.50", false));
		save(sessionFactory, OTHER_CATALOGUE, OTHER_CATALOGUE_RUN, SEEN_AT, product(RETAILER, unseen.getProductKey(), 7, listing(OTHER_CATALOGUE, "1.09", true)));
		save(sessionFactory, catalogue.id(), catalogue.run(), SEEN_AT, seen, unseen, inactive);
		save(sessionFactory, catalogue.id(), catalogue.laterRun(), LATER, seen);
		var latestStamp = takeChangeSequences(sessionFactory, 1).getFirst();

		assertEquals(1, deactivateUnseenListings(sessionFactory, catalogue.id(), catalogue.laterRun()));

		var products = findProducts(sessionFactory, RETAILER, seen.getProductKey(), unseen.getProductKey(), inactive.getProductKey());
		assertEquals(seen, products.get(seen.getProductKey()));
		assertEquals(inactive, products.get(inactive.getProductKey()));
		var deactivated = products.get(unseen.getProductKey());
		assertEquals(sortedByCatalogue(listing(catalogue.id(), "0.99", false), listing(OTHER_CATALOGUE, "1.09", true)), deactivated.getListings());
		assertTrue(deactivated.getChangeSequence() > latestStamp, "Not stamped anew: " + deactivated.getChangeSequence());
		assertEquals(seenByTheirRuns(catalogue), findSeen(unseen.getProductKey()));
	}

	@Test
	void deactivatesTheActiveListingsOfSomeProductsAndStampsThem(Mutiny.SessionFactory sessionFactory) throws SQLException {
		var catalogue = createCatalogueWithRuns(sessionFactory);
		var deleted = product(RETAILER, newKey(), 7, listing(catalogue.id(), "1.29", true), listing(OTHER_CATALOGUE, "1.39", true));
		var kept = product(RETAILER, newKey(), 7, listing(catalogue.id(), "0.99", true));
		var inactive = product(RETAILER, newKey(), 7, listing(catalogue.id(), "4.50", false));
		save(sessionFactory, OTHER_CATALOGUE, OTHER_CATALOGUE_RUN, SEEN_AT, product(RETAILER, deleted.getProductKey(), 7, listing(OTHER_CATALOGUE, "1.39", true)));
		save(sessionFactory, catalogue.id(), catalogue.run(), SEEN_AT, deleted, kept, inactive);
		var latestStamp = takeChangeSequences(sessionFactory, 1).getFirst();

		assertEquals(1, deactivateListings(sessionFactory, catalogue.id(), deleted.getProductKey(), inactive.getProductKey(), newKey()));

		var products = findProducts(sessionFactory, RETAILER, deleted.getProductKey(), kept.getProductKey(), inactive.getProductKey());
		assertEquals(kept, products.get(kept.getProductKey()));
		assertEquals(inactive, products.get(inactive.getProductKey()));
		var deactivated = products.get(deleted.getProductKey());
		assertEquals(sortedByCatalogue(listing(catalogue.id(), "1.29", false), listing(OTHER_CATALOGUE, "1.39", true)), deactivated.getListings());
		assertTrue(deactivated.getChangeSequence() > latestStamp, "Not stamped anew: " + deactivated.getChangeSequence());
		assertEquals(seenByTheirRuns(catalogue), findSeen(deleted.getProductKey()));
	}

	@Test
	void deactivatesNothingForNoKeys(Mutiny.SessionFactory sessionFactory) {
		assertEquals(0, deactivateListings(sessionFactory, CATALOGUE));
	}

	@Test
	void takesAscendingValuesOfTheChangeSequence(Mutiny.SessionFactory sessionFactory) {
		var first = takeChangeSequences(sessionFactory, 3);
		var second = takeChangeSequences(sessionFactory, 2);

		assertEquals(3, first.size());
		assertEquals(2, second.size());
		var all = new ArrayList<>(first);
		all.addAll(second);
		for (int i = 1; i < all.size(); i++) {
			assertTrue(all.get(i) > all.get(i - 1), "Not ascending: " + all);
		}
	}

	@Test
	void takesNoValuesOfTheChangeSequence(Mutiny.SessionFactory sessionFactory) {
		assertEquals(List.of(), takeChangeSequences(sessionFactory, 0));
	}

	@Test
	void findsTheChangesAfterAValueOfTheChangeSequenceTheLowestFirst(Mutiny.SessionFactory sessionFactory) {
		var sequences = takeChangeSequences(sessionFactory, 4);
		var first = product(RETAILER, newKey(), sequences.get(0), listing(CATALOGUE, "1.29", true));
		var second = ImmutableProductChange.builder()
				.from(product(RETAILER, newKey(), sequences.get(1), listing(OTHER_CATALOGUE, "1.09", false)))
				.names(Map.of("lt", "Sviestas"))
				.build();
		var third = product(OTHER_RETAILER, newKey(), sequences.get(2), listing(OTHER_RETAILERS_CATALOGUE, "2.10", true));
		var fourth = product(RETAILER, newKey(), sequences.get(3), listing(CATALOGUE, "3.20", true));
		save(sessionFactory, CATALOGUE, RUN, SEEN_AT, fourth, first);
		save(sessionFactory, OTHER_CATALOGUE, OTHER_CATALOGUE_RUN, SEEN_AT, second);
		save(sessionFactory, OTHER_RETAILERS_CATALOGUE, OTHER_RETAILERS_RUN, SEEN_AT, third);

		assertEquals(List.of(second, third), findChangesAfter(sessionFactory, sequences.get(0), 2));
		assertEquals(List.of(fourth), findChangesAfter(sessionFactory, sequences.get(2), 2));
		assertEquals(List.of(), findChangesAfter(sessionFactory, sequences.get(3), 2));
	}

	/**
	 * Each page is read after the last change of the one before, and some products change between the pages: those read
	 * already are read again, at their new place, and those not read yet are read once, at their new place.
	 */
	@Test
	void readsEveryChangeInOrderWhileProductsChangeBetweenThePages(Mutiny.SessionFactory sessionFactory) {
		var start = takeChangeSequences(sessionFactory, 1).getFirst();
		var keys = Stream.generate(RetailerProductDaoImplTest::newKey).limit(3000).toList();
		for (int i = 0; i < keys.size(); i += 500) {
			stamp(sessionFactory, keys.subList(i, i + 500), "1.00");
		}

		var read = new ArrayList<ProductChange>();
		var cursor = start;
		List<ProductChange> page;
		for (int pages = 1; !(page = findChangesAfter(sessionFactory, cursor, 250)).isEmpty(); pages++) {
			read.addAll(page);
			cursor = page.getLast().getChangeSequence();
			if (pages <= 4) {
				stamp(sessionFactory, List.of(keys.get(pages * 100), keys.get(keys.size() - pages)), "2.0" + pages);
			}
		}

		for (int i = 1; i < read.size(); i++) {
			assertTrue(read.get(i).getChangeSequence() > read.get(i - 1).getChangeSequence(), "Not ascending at " + i);
		}
		assertEquals(keys.size() + 4, read.size());
		var latest = read.stream().collect(Collectors.toMap(ProductChange::getProductKey, Function.identity(), (earlier, later) -> later));
		assertEquals(findProducts(sessionFactory, RETAILER, keys.toArray(ProductKey[]::new)), latest);
	}

	@Test
	void deletesTheInactiveListingsLastSeenBeforeATime(Mutiny.SessionFactory sessionFactory) {
		var evicted = product(RETAILER, newKey(), 7, listing(CATALOGUE, "1.29", false), listing(OTHER_CATALOGUE, "1.39", true));
		var active = product(RETAILER, newKey(), 7, listing(CATALOGUE, "0.99", true));
		var recentlyInactive = product(RETAILER, newKey(), 7, listing(CATALOGUE, "4.50", false));
		save(sessionFactory, OTHER_CATALOGUE, OTHER_CATALOGUE_RUN, SEEN_AT, product(RETAILER, evicted.getProductKey(), 7, listing(OTHER_CATALOGUE, "1.39", true)));
		save(sessionFactory, CATALOGUE, RUN, LONG_AGO, evicted, active);
		save(sessionFactory, CATALOGUE, RUN, SEEN_AT, recentlyInactive);

		assertEquals(1, deleteInactiveListings(sessionFactory, LONG_AGO.plusDays(1)));

		var products = findProducts(sessionFactory, RETAILER, evicted.getProductKey(), active.getProductKey(), recentlyInactive.getProductKey());
		assertEquals(ImmutableProductChange.copyOf(evicted).withListings(listing(OTHER_CATALOGUE, "1.39", true)), products.get(evicted.getProductKey()));
		assertEquals(active, products.get(active.getProductKey()));
		assertEquals(recentlyInactive, products.get(recentlyInactive.getProductKey()));
	}

	@Test
	void makesTheProductsLeftWithoutListingsTombstonesOnce(Mutiny.SessionFactory sessionFactory) throws SQLException {
		var unlisted = product(RETAILER, newKey(), 7, listing(CATALOGUE, "1.29", false));
		var listedElsewhere = product(RETAILER, newKey(), 7, listing(CATALOGUE, "0.99", false), listing(OTHER_CATALOGUE, "1.09", true));
		save(sessionFactory, OTHER_CATALOGUE, OTHER_CATALOGUE_RUN, SEEN_AT, product(RETAILER, listedElsewhere.getProductKey(), 7, listing(OTHER_CATALOGUE, "1.09", true)));
		save(sessionFactory, CATALOGUE, RUN, LONG_AGO, unlisted, listedElsewhere);
		deleteInactiveListings(sessionFactory, LONG_AGO.plusDays(1));
		var latestStamp = takeChangeSequences(sessionFactory, 1).getFirst();

		assertEquals(1, removeUnlistedProducts(sessionFactory, REMOVED_AT));
		var tombstone = findProducts(sessionFactory, RETAILER, unlisted.getProductKey()).get(unlisted.getProductKey());
		assertEquals(0, removeUnlistedProducts(sessionFactory, REMOVED_AT.plusDays(1)));

		assertTrue(tombstone.isRemoved(), "Not a tombstone");
		assertEquals(Map.of(), tombstone.getNames());
		assertEquals(List.of(), tombstone.getListings());
		assertTrue(tombstone.getChangeSequence() > latestStamp, "Not stamped anew: " + tombstone.getChangeSequence());
		var products = findProducts(sessionFactory, RETAILER, unlisted.getProductKey(), listedElsewhere.getProductKey());
		assertEquals(tombstone, products.get(unlisted.getProductKey()));
		assertEquals(ImmutableProductChange.copyOf(listedElsewhere).withListings(listing(OTHER_CATALOGUE, "1.09", true)), products.get(listedElsewhere.getProductKey()));
		assertEquals(REMOVED_AT, findRemovedAt(unlisted.getProductKey()));
	}

	@Test
	void revivesATombstoneWithTheSameId(Mutiny.SessionFactory sessionFactory) throws SQLException {
		var product = product(RETAILER, newKey(), 7, listing(CATALOGUE, "1.29", false));
		save(sessionFactory, CATALOGUE, RUN, LONG_AGO, product);
		var id = findId(product.getProductKey());
		deleteInactiveListings(sessionFactory, LONG_AGO.plusDays(1));
		removeUnlistedProducts(sessionFactory, REMOVED_AT);
		var revived = product(RETAILER, product.getProductKey(), 9, listing(CATALOGUE, "1.19", true));

		save(sessionFactory, CATALOGUE, LATER_RUN, LATER, revived);

		assertEquals(Map.of(revived.getProductKey(), revived), findProducts(sessionFactory, RETAILER, product.getProductKey()));
		assertEquals(id, findId(product.getProductKey()));
		assertNull(findRemovedAt(product.getProductKey()));
	}

	@Test
	void purgesTheTombstonesMadeBeforeATime(Mutiny.SessionFactory sessionFactory) {
		var purged = product(RETAILER, newKey(), 7, listing(CATALOGUE, "1.29", false));
		var kept = product(RETAILER, newKey(), 7, listing(CATALOGUE, "0.99", false));
		var listed = product(RETAILER, newKey(), 7, listing(CATALOGUE, "4.50", true));
		save(sessionFactory, CATALOGUE, RUN, LONG_AGO, purged, listed);
		deleteInactiveListings(sessionFactory, LONG_AGO.plusDays(1));
		removeUnlistedProducts(sessionFactory, PURGED_REMOVED_AT);
		save(sessionFactory, CATALOGUE, RUN, LONG_AGO, kept);
		deleteInactiveListings(sessionFactory, LONG_AGO.plusDays(1));
		removeUnlistedProducts(sessionFactory, PURGED_REMOVED_AT.plusMonths(6));

		assertEquals(1, purgeRemovedProducts(sessionFactory, PURGED_REMOVED_AT.plusMonths(3)));

		var products = findProducts(sessionFactory, RETAILER, purged.getProductKey(), kept.getProductKey(), listed.getProductKey());
		assertEquals(Set.of(kept.getProductKey(), listed.getProductKey()), products.keySet());
		assertTrue(products.get(kept.getProductKey()).isRemoved(), "Not a tombstone");
		assertEquals(listed, products.get(listed.getProductKey()));
	}

	@Test
	void takingValuesWaitsForATransactionHoldingTheChangeSequenceBeforeItTakesAny(Mutiny.SessionFactory sessionFactory) throws Exception {
		var sut = new RetailerProductDaoImpl();

		var outcome = stampWhileHeld(sessionFactory, tx -> sut.holdChangeSequence(tx).replaceWith(0L), tx -> sut.takeChangeSequences(tx, 1));

		assertEquals(1, outcome.stamped().size());
	}

	@Test
	void makingTombstonesWaitsForATransactionHoldingTheChangeSequence(Mutiny.SessionFactory sessionFactory) throws Exception {
		var sut = new RetailerProductDaoImpl();
		var unlisted = product(RETAILER, newKey(), 7, listing(CATALOGUE, "1.29", false));
		save(sessionFactory, CATALOGUE, RUN, LONG_AGO, unlisted);
		deleteInactiveListings(sessionFactory, LONG_AGO.plusDays(1));

		var outcome = stampWhileHeld(sessionFactory, tx -> sut.removeUnlistedProducts(tx, REMOVED_AT));

		assertEquals(1, outcome.stamped());
		var stamp = findProducts(sessionFactory, RETAILER, unlisted.getProductKey()).get(unlisted.getProductKey()).getChangeSequence();
		assertTrue(stamp > outcome.held(), "Stamped before the held value: " + stamp + ", " + outcome);
	}

	@Test
	void takingValuesOfTheChangeSequenceWaitsForATransactionHoldingIt(Mutiny.SessionFactory sessionFactory) throws Exception {
		var sut = new RetailerProductDaoImpl();

		var outcome = stampWhileHeld(sessionFactory, tx -> sut.takeChangeSequences(tx, 1));

		assertTrue(outcome.stamped().getFirst() > outcome.held(), "Taken before the held value: " + outcome);
	}

	@Test
	void deactivatingTheListingsARunDidNotSeeWaitsForATransactionHoldingTheChangeSequence(Mutiny.SessionFactory sessionFactory) throws Exception {
		var sut = new RetailerProductDaoImpl();
		var catalogue = createCatalogueWithRuns(sessionFactory);
		var unseen = product(RETAILER, newKey(), 7, listing(catalogue.id(), "0.99", true));
		save(sessionFactory, catalogue.id(), catalogue.run(), SEEN_AT, unseen);

		var outcome = stampWhileHeld(sessionFactory, tx -> sut.deactivateUnseenListings(tx, catalogue.id(), catalogue.laterRun()));

		assertEquals(1, outcome.stamped());
		var stamp = findProducts(sessionFactory, RETAILER, unseen.getProductKey()).get(unseen.getProductKey()).getChangeSequence();
		assertTrue(stamp > outcome.held(), "Stamped before the held value: " + stamp + ", " + outcome);
	}

	@Test
	void deactivatingTheListingsOfSomeProductsWaitsForATransactionHoldingTheChangeSequence(Mutiny.SessionFactory sessionFactory) throws Exception {
		var sut = new RetailerProductDaoImpl();
		var catalogue = createCatalogueWithRuns(sessionFactory);
		var deleted = product(RETAILER, newKey(), 7, listing(catalogue.id(), "0.99", true));
		save(sessionFactory, catalogue.id(), catalogue.run(), SEEN_AT, deleted);

		var outcome = stampWhileHeld(sessionFactory, tx -> sut.deactivateListings(tx, RETAILER, catalogue.id(), Set.of(deleted.getProductKey())));

		assertEquals(1, outcome.stamped());
		var stamp = findProducts(sessionFactory, RETAILER, deleted.getProductKey()).get(deleted.getProductKey()).getChangeSequence();
		assertTrue(stamp > outcome.held(), "Stamped before the held value: " + stamp + ", " + outcome);
	}

	/**
	 * A Catalogue of its own for a test that counts or deactivates all the Listings of a Catalogue.
	 */
	private record TestCatalogue(CatalogueId id, IngestionRunId run, IngestionRunId laterRun) {
	}

	private static TestCatalogue createCatalogueWithRuns(Mutiny.SessionFactory sessionFactory) {
		var catalogue = new TestCatalogue(
				new CatalogueIdImpl("test-" + UUID.randomUUID().toString().substring(0, 8)),
				new IngestionRunIdImpl(UUID.randomUUID().toString()),
				new IngestionRunIdImpl(UUID.randomUUID().toString())
		);
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> tx.persist(catalogue(catalogue.id(), RETAILER, "LT", "lt"))
						.chain(() -> tx.persistAll(ingestionRun(catalogue.run(), catalogue.id()), ingestionRun(catalogue.laterRun(), catalogue.id()))))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		return catalogue;
	}

	/**
	 * Listings in the order a product reads back with.
	 */
	private static List<Listing> sortedByCatalogue(Listing... listings) {
		return Stream.of(listings).sorted(Comparator.comparing(l -> l.getCatalogueId().asString())).toList();
	}

	/**
	 * What {@link #findSeen(ProductKey)} reads for a product that the first run of a test Catalogue and the run of
	 * {@link #OTHER_CATALOGUE} carried at {@link #SEEN_AT}, and no run since.
	 */
	private static List<List<String>> seenByTheirRuns(TestCatalogue catalogue) {
		return Stream.of(
						List.of(catalogue.id().asString(), SEEN_AT.toString(), catalogue.run().asString()),
						List.of(OTHER_CATALOGUE.asString(), SEEN_AT.toString(), OTHER_CATALOGUE_RUN.asString()))
				.sorted(Comparator.comparing(List::getFirst))
				.toList();
	}

	private static IngestionRunEntity ingestionRun(IngestionRunId runId, CatalogueId catalogueId) {
		return TestEntities.ingestionRun(runId, catalogueId, IngestionRunStatus.PROCESSING, LocalDateTime.of(2026, 10, 21, 9, 0, 0));
	}

	private static ProductKey newKey() {
		return new ProductKeyImpl("P-" + UUID.randomUUID());
	}

	private static ProductChange product(RetailerId retailerId, ProductKey key, long changeSequence, Listing... listings) {
		return ImmutableProductChange.builder()
				.retailerId(retailerId)
				.productKey(key)
				.changeSequence(changeSequence)
				.isRemoved(false)
				.inputHash("a".repeat(64))
				.names(Map.of("lt", "Pienas", "en", "Milk"))
				.characteristics(Map.of("fat", "2.5%"))
				.tags(List.of("dairy", "local"))
				.addListings(listings)
				.build();
	}

	/**
	 * The price has the scale of its column, so that the product reads back equal.
	 */
	private static Listing listing(CatalogueId catalogueId, String price, boolean active) {
		return ImmutableListing.builder().catalogueId(catalogueId).price(new BigDecimal(price).setScale(4)).currency(EUR).isActive(active).build();
	}

	private static void save(Mutiny.SessionFactory sessionFactory, CatalogueId catalogueId, IngestionRunId runId, LocalDateTime seenAt, ProductChange... products) {
		var sut = new RetailerProductDaoImpl();
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.save(tx, catalogueId, runId, seenAt, List.of(products)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static Map<ProductKey, ProductChange> findProducts(Mutiny.SessionFactory sessionFactory, RetailerId retailerId, ProductKey... keys) {
		var sut = new RetailerProductDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.findProducts(em, retailerId, Set.of(keys)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	/**
	 * What a stamp returned, after waiting for a transaction that held a value of the change sequence.
	 *
	 * @param held    The value that the holding transaction took
	 * @param stamped What the stamp returned
	 */
	private record HeldStamp<T>(long held, T stamped) {
	}

	/**
	 * Run a stamp while another transaction holds a value of the change sequence it took, and check that the stamp waits
	 * for that transaction to end.
	 */
	private static <T> HeldStamp<T> stampWhileHeld(Mutiny.SessionFactory sessionFactory, Function<ReactivePersistenceTxContext, Uni<T>> stamp) throws Exception {
		var sut = new RetailerProductDaoImpl();
		return stampWhileHeld(sessionFactory, tx -> sut.takeChangeSequences(tx, 1).map(List::getFirst), stamp);
	}

	/**
	 * Run a stamp while another transaction holds the change sequence, and check that the stamp waits for that
	 * transaction to end. The transactions are told apart from the database's locks. The holding transaction goes on,
	 * when told to end, on the event loop of its session, as Hibernate Reactive requires.
	 *
	 * @param hold What the holding transaction does to hold the change sequence, answering the value it took
	 */
	private static <T> HeldStamp<T> stampWhileHeld(Mutiny.SessionFactory sessionFactory, Function<ReactivePersistenceTxContext, Uni<Long>> hold, Function<ReactivePersistenceTxContext, Uni<T>> stamp) throws Exception {
		var persistenceContextFactory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var end = new CompletableFuture<Void>();
		var holding = persistenceContextFactory
				.withTransaction(tx -> {
					var context = Vertx.currentContext();
					return hold.apply(tx).call(() -> Uni.createFrom().completionStage(end).emitOn(task -> context.runOnContext(v -> task.run())));
				})
				.subscribeAsCompletionStage();
		try {
			awaitAdvisoryLocks(true);
			var stamping = persistenceContextFactory.withTransaction(stamp).subscribeAsCompletionStage();
			awaitAdvisoryLocks(false);

			assertFalse(stamping.isDone(), "The stamp did not wait");
			end.complete(null);
			var held = holding.get(ASYNC_WAIT_SECONDS, SECONDS);
			return new HeldStamp<>(held, stamping.get(ASYNC_WAIT_SECONDS, SECONDS));
		} finally {
			end.complete(null);
		}
	}

	/**
	 * Wait until a transaction holds an advisory lock, or waits for one.
	 */
	private static void awaitAdvisoryLocks(boolean granted) throws SQLException, InterruptedException {
		for (int i = 0; i < 600; i++) {
			try (
					var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
					var s = c.prepareStatement("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND granted = ?")
			) {
				s.setBoolean(1, granted);
				try (var rs = s.executeQuery()) {
					rs.next();
					if (rs.getLong(1) > 0) return;
				}
			}
			Thread.sleep(50);
		}
		fail("No transaction " + (granted ? "holds" : "waits for") + " an advisory lock");
	}

	/**
	 * Give products of {@link #RETAILER} listed in {@link #CATALOGUE} a price, each with a new value of the change
	 * sequence, in one transaction.
	 */
	private static void stamp(Mutiny.SessionFactory sessionFactory, List<ProductKey> keys, String price) {
		var sut = new RetailerProductDaoImpl();
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.takeChangeSequences(tx, keys.size()).flatMap(sequences -> {
					var next = sequences.iterator();
					return sut.save(tx, CATALOGUE, RUN, SEEN_AT, keys.stream().map(key -> product(RETAILER, key, next.next(), listing(CATALOGUE, price, true))).toList());
				}))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static List<ProductChange> findChangesAfter(Mutiny.SessionFactory sessionFactory, long after, int limit) {
		var sut = new RetailerProductDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.findChangesAfter(em, after, limit))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static List<Long> takeChangeSequences(Mutiny.SessionFactory sessionFactory, int count) {
		var sut = new RetailerProductDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.takeChangeSequences(tx, count))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static long countActiveListings(Mutiny.SessionFactory sessionFactory, CatalogueId catalogueId) {
		var sut = new RetailerProductDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.countActiveListings(em, catalogueId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static long countUnseenListings(Mutiny.SessionFactory sessionFactory, CatalogueId catalogueId, IngestionRunId runId) {
		var sut = new RetailerProductDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.countUnseenListings(em, catalogueId, runId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static int deactivateUnseenListings(Mutiny.SessionFactory sessionFactory, CatalogueId catalogueId, IngestionRunId runId) {
		var sut = new RetailerProductDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.deactivateUnseenListings(tx, catalogueId, runId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static int deactivateListings(Mutiny.SessionFactory sessionFactory, CatalogueId catalogueId, ProductKey... keys) {
		var sut = new RetailerProductDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.deactivateListings(tx, RETAILER, catalogueId, Set.of(keys)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static int deleteInactiveListings(Mutiny.SessionFactory sessionFactory, LocalDateTime lastSeenBefore) {
		var sut = new RetailerProductDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.deleteInactiveListings(tx, lastSeenBefore))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static int removeUnlistedProducts(Mutiny.SessionFactory sessionFactory, LocalDateTime removedAt) {
		var sut = new RetailerProductDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.removeUnlistedProducts(tx, removedAt))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static int purgeRemovedProducts(Mutiny.SessionFactory sessionFactory, LocalDateTime removedBefore) {
		var sut = new RetailerProductDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> sut.purgeRemovedProducts(tx, removedBefore))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	/**
	 * Reads with plain JDBC, not JPA: the id is assigned by the DAO and never read from it.
	 */
	private static String findId(ProductKey key) throws SQLException {
		return findColumn("id", key, rs -> rs.getString(1));
	}

	/**
	 * Reads with plain JDBC, not JPA: the test is about the mapping of the column, which reading through that same mapping
	 * could not check.
	 */
	private static LocalDateTime findRemovedAt(ProductKey key) throws SQLException {
		return findColumn("removed_at", key, rs -> rs.getTimestamp(1) != null ? rs.getTimestamp(1).toLocalDateTime() : null);
	}

	private interface ColumnReader<T> {
		T read(ResultSet resultSet) throws SQLException;
	}

	private static <T> T findColumn(String column, ProductKey key, ColumnReader<T> reader) throws SQLException {
		try (
				var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
				var s = c.prepareStatement("SELECT " + column + " FROM IN_RETAILER_PRODUCT WHERE product_key = ?")
		) {
			s.setString(1, key.asString());
			try (var rs = s.executeQuery()) {
				rs.next();
				return reader.read(rs);
			}
		}
	}

	/**
	 * Reads with plain JDBC, not JPA: the test is about the mapping of the columns, which reading through that same
	 * mapping could not check.
	 */
	private static List<List<String>> findSeen(ProductKey key) throws SQLException {
		var seen = new ArrayList<List<String>>();
		try (
				var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
				var s = c.prepareStatement("""
						SELECT l.catalogue_id, l.last_seen_at, l.last_seen_run_id
						FROM IN_LISTING l JOIN IN_RETAILER_PRODUCT p ON p.id = l.retailer_product_id
						WHERE p.product_key = ?
						ORDER BY l.catalogue_id
						""")
		) {
			s.setString(1, key.asString());
			try (var rs = s.executeQuery()) {
				while (rs.next()) {
					seen.add(List.of(rs.getString(1), rs.getTimestamp(2).toLocalDateTime().toString(), rs.getString(3)));
				}
			}
		}
		return seen;
	}

	private static void execute(String sql) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement()) {
			s.execute(sql);
		}
	}
}
