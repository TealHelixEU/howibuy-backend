package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import eu.tealhelix.catalogue.v1.model.ImmutableListing;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import eu.tealhelix.howibuy.dao.jpa.CatalogueEntity;
import eu.tealhelix.howibuy.dao.jpa.ClassificationAttemptEntity;
import eu.tealhelix.howibuy.dao.jpa.ClassificationEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerEntity;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
import eu.tealhelix.howibuy.v1.types.Classifier;
import eu.tealhelix.howibuy.v1.types.RetailerProductId;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A product is pending while it has no Classification, or its Classification was resolved from another input hash than
 * its current one.
 */
@Testcontainers
public class ClassificationDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID RETAILER = UUID.fromString("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11");
	private static final String CATALOGUE = "test-gr";

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "howibuy.db.changelog.xml", "test");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@BeforeAll
	static void setUpRetailer(Mutiny.SessionFactory sessionFactory) {
		var retailer = new RetailerEntity();
		retailer.setId(RETAILER);
		retailer.setName("Test Retailer");
		retailer.setActive(true);
		var catalogue = new CatalogueEntity();
		catalogue.setId(CATALOGUE);
		catalogue.setRetailerId(RETAILER);
		catalogue.setCountry("GR");
		catalogue.setPrimaryLanguage("el");
		catalogue.setActive(true);
		sessionFactory.withTransaction(session -> session.persistAll(retailer, catalogue)).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	@BeforeEach
	void setUp() throws SQLException {
		execute(
				"DELETE FROM TH_LISTING",
				"DELETE FROM TH_RETAILER_PRODUCT_NAME",
				"DELETE FROM TH_RETAILER_PRODUCT"
		);
	}

	@Test
	void aNewProductIsPendingWithItsInputHashAndLanguages(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt")));

		var pending = findPending(sessionFactory, 10);

		assertEquals(1, pending.size());
		assertEquals("h1", pending.getFirst().getInputHash());
		assertEquals(Set.of("el", "en"), pending.getFirst().getLanguages());
	}

	@Test
	void aProductClassifiedFromItsCurrentInputHashIsNotPending(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")));
		classify(sessionFactory, findPending(sessionFactory, 10).getFirst().getId(), "h1");

		assertEquals(List.of(), findPending(sessionFactory, 10));
	}

	@Test
	void aProductWhoseInputHashChangedIsPendingAgain(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")));
		var id = findPending(sessionFactory, 10).getFirst().getId();
		classify(sessionFactory, id, "h1");

		apply(sessionFactory, product("yoghurt", 2, "h2", Map.of("el", "Γιαούρτι στραγγιστό")));

		var pending = findPending(sessionFactory, 10);
		assertEquals(List.of(id), pending.stream().map(PendingProduct::getId).toList());
		assertEquals("h2", pending.getFirst().getInputHash());
	}

	/**
	 * The Ingestion leaves the input hash of a product alone when only its price changes.
	 */
	@Test
	void aPriceOnlyChangeKeepsTheProductClassified(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι"), "1.99"));
		classify(sessionFactory, findPending(sessionFactory, 10).getFirst().getId(), "h1");

		apply(sessionFactory, product("yoghurt", 2, "h1", Map.of("el", "Γιαούρτι"), "1.79"));

		assertEquals(List.of(), findPending(sessionFactory, 10));
	}

	@Test
	void theOldestChangesComeFirstUpToTheLimit(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory,
				product("feta", 3, "h3", Map.of("el", "Φέτα")),
				product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")),
				product("honey", 2, "h2", Map.of("el", "Μέλι")));

		var pending = findPending(sessionFactory, 2);

		assertEquals(List.of("h1", "h2"), pending.stream().map(PendingProduct::getInputHash).toList());
	}

	@Test
	void aRemovedProductTakesItsClassificationAndAttemptsWithIt(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")));
		var id = findPending(sessionFactory, 10).getFirst().getId();
		classify(sessionFactory, id, "h1");

		apply(sessionFactory, removed("yoghurt", 2));

		assertEquals(0, scalar("SELECT count(*) FROM TH_CLASSIFICATION"));
		assertEquals(0, scalar("SELECT count(*) FROM TH_CLASSIFICATION_ATTEMPT"));
	}

	/**
	 * As many products as the largest page the Catalogue Sync asks for.
	 */
	@Test
	void findsManyPendingProductsAtOnce(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, IntStream.rangeClosed(1, 1000)
				.mapToObj(i -> product("product-" + i, i, "h" + i, Map.of("el", "Προϊόν " + i, "en", "Product " + i)))
				.toArray(ProductChange[]::new));

		var pending = findPending(sessionFactory, 1000);

		assertEquals(1000, pending.size());
		assertEquals(Set.of(Set.of("el", "en")), pending.stream().map(PendingProduct::getLanguages).collect(Collectors.toSet()));
	}

	private static List<PendingProduct> findPending(Mutiny.SessionFactory sessionFactory, int limit) {
		var sut = new ClassificationDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.findPending(em, limit))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	/**
	 * Classify the product, with an attempt and the Classification resolved from it, that no archetype matches.
	 */
	private static void classify(Mutiny.SessionFactory sessionFactory, RetailerProductId id, String inputHash) {
		var now = LocalDateTime.now();
		var attempt = new ClassificationAttemptEntity();
		attempt.setId(UUID.randomUUID());
		attempt.setRetailerProductId(id.asUuid());
		attempt.setLang("el");
		attempt.setClassifier(Classifier.MANUAL);
		attempt.setClassifierVersion("test");
		attempt.setInputHash(inputHash);
		attempt.setCreatedAt(now);
		var classification = new ClassificationEntity();
		classification.setRetailerProductId(id.asUuid());
		classification.setInputHash(inputHash);
		classification.setNeedsReview(false);
		classification.setResolvedAt(now);
		sessionFactory.withTransaction(session -> session.persistAll(attempt, classification)).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static void apply(Mutiny.SessionFactory sessionFactory, ProductChange... changes) {
		var dao = new RetailerProductDaoImpl();
		new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withTransaction(tx -> dao.apply(tx, List.of(changes)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static ProductChange product(String productKey, long changeSequence, String inputHash, Map<String, String> names) {
		return product(productKey, changeSequence, inputHash, names, "1.99");
	}

	private static ProductChange product(String productKey, long changeSequence, String inputHash, Map<String, String> names, String price) {
		return ImmutableProductChange.builder()
				.retailerId(new GenericRetailerId(RETAILER.toString()))
				.productKey(new ProductKeyImpl(productKey))
				.changeSequence(changeSequence)
				.isRemoved(false)
				.inputHash(inputHash)
				.names(names)
				.characteristics(Map.of())
				.tags(List.of())
				.addListings(ImmutableListing.builder()
						.catalogueId(new CatalogueIdImpl(CATALOGUE))
						.price(new BigDecimal(price))
						.currency(Currency.getInstance("EUR"))
						.isActive(true)
						.build())
				.build();
	}

	private static ProductChange removed(String productKey, long changeSequence) {
		return ImmutableProductChange.builder()
				.retailerId(new GenericRetailerId(RETAILER.toString()))
				.productKey(new ProductKeyImpl(productKey))
				.changeSequence(changeSequence)
				.isRemoved(true)
				.build();
	}

	private static void execute(String... statements) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement()) {
			for (var statement : statements) {
				s.execute(statement);
			}
		}
	}

	private static long scalar(String sql) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}
}
