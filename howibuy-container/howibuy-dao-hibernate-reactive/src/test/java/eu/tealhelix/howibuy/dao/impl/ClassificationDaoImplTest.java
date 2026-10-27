package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import eu.tealhelix.catalogue.v1.model.ImmutableListing;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.Listing;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeProductEntity;
import eu.tealhelix.howibuy.dao.jpa.CatalogueEntity;
import eu.tealhelix.howibuy.dao.jpa.ClassificationAttemptEntity;
import eu.tealhelix.howibuy.dao.jpa.ClassificationEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerEntity;
import eu.tealhelix.howibuy.dao.jpa.values.AnimalWelfareImpact;
import eu.tealhelix.howibuy.dao.jpa.values.EnvironmentalImpact;
import eu.tealhelix.howibuy.dao.jpa.values.SocialImpact;
import eu.tealhelix.howibuy.services.model.ImmutableProductClassificationInput;
import eu.tealhelix.howibuy.services.model.ImmutableProductToAssess;
import eu.tealhelix.howibuy.services.model.ImmutableProductToClassify;
import eu.tealhelix.howibuy.services.model.ProductToAssess;
import eu.tealhelix.howibuy.v1.model.Classification;
import eu.tealhelix.howibuy.v1.model.ClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.ClassificationToReview;
import eu.tealhelix.howibuy.v1.model.ImmutableClassification;
import eu.tealhelix.howibuy.v1.model.ImmutableClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.ImmutableClassificationToReview;
import eu.tealhelix.howibuy.v1.model.ImmutableMatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.MatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.Classifier;
import eu.tealhelix.howibuy.v1.types.RetailerProductId;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.RetailerProductIdImpl;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A product is pending while it has no Classification, its Classification was resolved from another input hash than
 * its current one, or its Classification was reopened. Recording an attempt locks the product, and stores the attempt
 * and the Classification. The assessment finds the products of a Catalogue's Retailer by their keys, with their
 * Classifications.
 */
@Testcontainers
public class ClassificationDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID RETAILER = UUID.fromString("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11");
	private static final String CATALOGUE = "test-gr";
	private static final String CATALOGUE_CY = "test-cy";
	private static final UUID OTHER_RETAILER = UUID.fromString("0c5d2e7a-3b41-4f6e-8a90-2d7b1c4e5f62");
	private static final String OTHER_RETAILERS_CATALOGUE = "other-gr";
	private static final ArchetypeProductId YOGHURT = new ArchetypeProductIdImpl("1b0a5a3e-4f7e-4d3f-9a51-0c2b6f1e7a01");
	private static final ArchetypeProductId KEFIR = new ArchetypeProductIdImpl("1b0a5a3e-4f7e-4d3f-9a51-0c2b6f1e7a02");
	private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 22, 12, 0);

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
		retailer.setClassificationLanguage("el");
		var catalogue = catalogue(CATALOGUE, RETAILER, "GR");
		var catalogueCy = catalogue(CATALOGUE_CY, RETAILER, "CY");
		var otherRetailer = new RetailerEntity();
		otherRetailer.setId(OTHER_RETAILER);
		otherRetailer.setName("Other Retailer");
		otherRetailer.setActive(true);
		var otherRetailersCatalogue = catalogue(OTHER_RETAILERS_CATALOGUE, OTHER_RETAILER, "GR");
		var category = new ArchetypeCategoryEntity();
		category.setId(UUID.fromString("1b0a5a3e-4f7e-4d3f-9a51-0c2b6f1e7a00"));
		category.setLevel((short) 3);
		category.setName("Yoghurts");
		var yoghurt = archetype(YOGHURT, category, "Yoghurt", "agb-y");
		var kefir = archetype(KEFIR, category, "Kefir", "agb-k");
		sessionFactory.withTransaction(session -> session.persistAll(retailer, catalogue, catalogueCy, otherRetailer, otherRetailersCatalogue, category).chain(() -> session.persistAll(yoghurt, kefir)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static ArchetypeProductEntity archetype(ArchetypeProductId id, ArchetypeCategoryEntity category, String name, String agbCode) {
		var archetype = new ArchetypeProductEntity();
		archetype.setId(id.asUuid());
		archetype.setCategory(category);
		archetype.setName(name);
		archetype.setAgbCode(agbCode);
		archetype.setEnvironmentalImpact(new EnvironmentalImpact());
		archetype.setSocialImpact(new SocialImpact());
		archetype.setAnimalWelfareImpact(new AnimalWelfareImpact());
		archetype.setNutriScore("Nutriscore_A");
		return archetype;
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

	@Test
	void findsTheProductToClassifyWithItsLanguagesAndItsRetailersClassificationLanguage(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt")));
		var id = findPending(sessionFactory, 10).getFirst().getId();

		var product = inTransaction(sessionFactory, tx -> new ClassificationDaoImpl().findLockedProductToClassify(tx, id));

		assertEquals(ImmutableProductToClassify.builder().inputHash("h1").languages(Set.of("el", "en")).classificationLanguage("el").build(), product);
	}

	@Test
	void anUnknownProductToClassifyIsNotFound(Mutiny.SessionFactory sessionFactory) {
		var product = inTransaction(sessionFactory, tx -> new ClassificationDaoImpl().findLockedProductToClassify(tx, new RetailerProductIdImpl(UUID.randomUUID().toString())));

		assertNull(product);
	}

	@Test
	void findsTheClassificationInputOfAProduct(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, ImmutableProductChange.builder()
				.from(product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt")))
				.characteristics(Map.of("Fat", "2%"))
				.tags(List.of("dairy", "chilled"))
				.build());
		var id = findPending(sessionFactory, 10).getFirst().getId();

		var input = new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> new ClassificationDaoImpl().findClassificationInput(em, id))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(ImmutableProductClassificationInput.builder()
				.inputHash("h1")
				.names(Map.of("el", "Γιαούρτι", "en", "Yoghurt"))
				.characteristics(Map.of("Fat", "2%"))
				.tags(List.of("dairy", "chilled"))
				.build(), input);
	}

	@Test
	void anUnknownProductHasNoClassificationInput(Mutiny.SessionFactory sessionFactory) {
		var input = new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> new ClassificationDaoImpl().findClassificationInput(em, new RetailerProductIdImpl(UUID.randomUUID().toString())))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertNull(input);
	}

	/**
	 * Another transaction asking for the lock without waiting is refused while the one that found the product runs.
	 */
	@Test
	void theProductToClassifyStaysLockedUntilTheTransactionEnds(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")));
		var id = findPending(sessionFactory, 10).getFirst().getId();

		var refusal = inTransaction(sessionFactory, tx -> new ClassificationDaoImpl().findLockedProductToClassify(tx, id)
				.map(product -> assertThrows(SQLException.class, () -> lockWithoutWaiting(id))));

		assertEquals("55P03", refusal.getSQLState(), "lock_not_available");
	}

	@Test
	void findsTheAttemptsMadeFromAnInputHash(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, product("yoghurt", 1, "h2", Map.of("el", "Γιαούρτι", "en", "Yoghurt")));
		var id = findPending(sessionFactory, 10).getFirst().getId();
		var stale = attempt("el", Classifier.LLM, YOGHURT, "h1", 0);
		var greek = attempt("el", Classifier.EXPERT, YOGHURT, "h2", 1);
		var english = attempt("en", Classifier.LLM, YOGHURT, "h2", 2, diagnostics("Dairy", "Fermented milk", "Yoghurts", "Yoghurt"));
		var dao = new ClassificationDaoImpl();
		inTransaction(sessionFactory, tx -> dao.recordAttempt(tx, id, stale)
				.chain(() -> dao.recordAttempt(tx, id, greek))
				.chain(() -> dao.recordAttempt(tx, id, english)));

		var attempts = new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> dao.findAttempts(em, id, "h2"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(Set.of(greek, english), Set.copyOf(attempts));
	}

	/**
	 * The levels the matching did not reach are left out, so a matching that reached none stores an empty object. An
	 * attempt without diagnostics stores none.
	 */
	@Test
	void storesTheDiagnosticsOfAnAttemptAsAnObjectOfTheLevelsReached(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt")));
		var id = findPending(sessionFactory, 10).getFirst().getId();
		var dao = new ClassificationDaoImpl();
		inTransaction(sessionFactory, tx -> dao.recordAttempt(tx, id, attempt("el", Classifier.LLM, null, "h1", 0, diagnostics("Dairy", "Fermented milk", null, null)))
				.chain(() -> dao.recordAttempt(tx, id, attempt("en", Classifier.LLM, null, "h1", 1, diagnostics(null, null, null, null))))
				.chain(() -> dao.recordAttempt(tx, id, attempt("el", Classifier.EXPERT, YOGHURT, "h1", 2))));

		var attempts = new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> dao.findAttempts(em, id, "h1"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(
				List.of("LLM|{\"l1Category\": \"Dairy\", \"l2Category\": \"Fermented milk\"}", "LLM|{}", "EXPERT|null"),
				rows("SELECT classifier, diagnostics::text FROM TH_CLASSIFICATION_ATTEMPT ORDER BY created_at"));
		assertEquals(
				Set.of(Optional.of(diagnostics("Dairy", "Fermented milk", null, null)), Optional.of(diagnostics(null, null, null, null)), Optional.empty()),
				attempts.stream().map(attempt -> Optional.ofNullable(attempt.getDiagnostics())).collect(Collectors.toSet()));
	}

	@Test
	void savingAClassificationReplacesTheOneBefore(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory, product("yoghurt", 1, "h2", Map.of("el", "Γιαούρτι")));
		var id = findPending(sessionFactory, 10).getFirst().getId();
		var dao = new ClassificationDaoImpl();
		inTransaction(sessionFactory, tx -> dao.saveClassification(tx, id, classification(YOGHURT, "h1", true, 0)));

		inTransaction(sessionFactory, tx -> dao.saveClassification(tx, id, classification(Classifier.MANUAL, null, "h2", false, 1)));

		assertEquals(
				List.of("MANUAL|null|h2|false|" + T0.plusMinutes(1)),
				rows("SELECT classifier, archetype_product_id, input_hash, needs_review, resolved_at FROM TH_CLASSIFICATION WHERE retailer_product_id = '" + id.asString() + "'"));
		assertEquals(List.of(), findPending(sessionFactory, 10));
	}

	@Test
	void savesTheArchetypeOfAClassification(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")));
		var id = findPending(sessionFactory, 10).getFirst().getId();

		inTransaction(sessionFactory, tx -> new ClassificationDaoImpl().saveClassification(tx, id, classification(YOGHURT, "h1", true, 0)));

		assertEquals(
				List.of("EXPERT|" + YOGHURT.asString() + "|h1|true|" + T0 + "|false"),
				rows("SELECT classifier, archetype_product_id, input_hash, needs_review, resolved_at, reopened FROM TH_CLASSIFICATION WHERE retailer_product_id = '" + id.asString() + "'"));
	}

	@Test
	void aClassificationToReviewIsFoundWithTheNamesOfTheProductAndTheAttemptsOfItsInputHashTheLatestFirst(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, product("yoghurt", 1, "h2", Map.of("en", "Yoghurt", "el", "Γιαούρτι")));
		var id = findPending(sessionFactory, 10).getFirst().getId();
		var stale = attempt("el", Classifier.MANUAL, YOGHURT, "h1", 0);
		var greek = attempt("el", Classifier.EXPERT, YOGHURT, "h2", 1);
		var english = attempt("en", Classifier.EXPERT, null, "h2", 2);
		var classification = classification(YOGHURT, "h2", true, 2);
		persist(sessionFactory, review(id, classification, stale, greek, english));

		var toReview = findToReview(sessionFactory, 10);

		assertEquals(List.of(ImmutableClassificationToReview.builder()
				.id(id)
				.names(Map.of("el", "Γιαούρτι", "en", "Yoghurt"))
				.classification(classification)
				.attempts(List.of(english, greek))
				.build()), toReview);
		assertEquals(List.of("el", "en"), List.copyOf(toReview.getFirst().getNames().keySet()), "the names in the order of their languages");
	}

	@Test
	void aClassificationThatNeedsNoReviewIsNotFound(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")));
		var id = findPending(sessionFactory, 10).getFirst().getId();
		persist(sessionFactory, review(id, classification(YOGHURT, "h1", false, 0), attempt("el", Classifier.EXPERT, YOGHURT, "h1", 0)));

		assertEquals(List.of(), findToReview(sessionFactory, 10));
	}

	/**
	 * The product is pending again, and the Classification resolved from its new names decides whether it needs review.
	 */
	@Test
	void aClassificationToReviewOfAProductChangedSinceIsNotFound(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")));
		var id = findPending(sessionFactory, 10).getFirst().getId();
		persist(sessionFactory, review(id, classification(YOGHURT, "h1", true, 0), attempt("el", Classifier.EXPERT, YOGHURT, "h1", 0)));

		apply(sessionFactory, product("yoghurt", 2, "h2", Map.of("el", "Γιαούρτι στραγγιστό")));

		assertEquals(List.of(), findToReview(sessionFactory, 10));
	}

	@Test
	void theOldestResolutionsComeFirstUpToTheLimit(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory,
				product("feta", 1, "h1", Map.of("el", "Φέτα")),
				product("yoghurt", 2, "h2", Map.of("el", "Γιαούρτι")),
				product("honey", 3, "h3", Map.of("el", "Μέλι")));
		var ids = findPending(sessionFactory, 10).stream().collect(Collectors.toMap(PendingProduct::getInputHash, PendingProduct::getId));
		var fixtures = new ArrayList<>();
		fixtures.addAll(review(ids.get("h1"), classification(YOGHURT, "h1", true, 3), attempt("el", Classifier.EXPERT, YOGHURT, "h1", 3)));
		fixtures.addAll(review(ids.get("h2"), classification(YOGHURT, "h2", true, 1), attempt("el", Classifier.EXPERT, YOGHURT, "h2", 1)));
		fixtures.addAll(review(ids.get("h3"), classification(YOGHURT, "h3", true, 2), attempt("el", Classifier.EXPERT, YOGHURT, "h3", 2)));
		persist(sessionFactory, fixtures);

		var toReview = findToReview(sessionFactory, 2);

		assertEquals(List.of(ids.get("h2"), ids.get("h3")), toReview.stream().map(ClassificationToReview::getId).toList());
	}

	/**
	 * As many products as the largest page asked for.
	 */
	@Test
	void findsManyClassificationsToReviewAtOnce(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, IntStream.rangeClosed(1, 1000)
				.mapToObj(i -> product("product-" + i, i, "h" + i, Map.of("el", "Προϊόν " + i, "en", "Product " + i)))
				.toArray(ProductChange[]::new));
		var fixtures = new ArrayList<>();
		for (var product : findPending(sessionFactory, 1000)) {
			var hash = product.getInputHash();
			fixtures.addAll(review(product.getId(), classification(YOGHURT, hash, true, 0), attempt("el", Classifier.EXPERT, YOGHURT, hash, 0), attempt("en", Classifier.EXPERT, null, hash, 0)));
		}
		persist(sessionFactory, fixtures);

		var toReview = findToReview(sessionFactory, 1000);

		assertEquals(1000, toReview.size());
		assertEquals(Set.of(Set.of("el", "en")), toReview.stream().map(product -> product.getNames().keySet()).collect(Collectors.toSet()));
		assertEquals(Set.of(2), toReview.stream().map(product -> product.getAttempts().size()).collect(Collectors.toSet()));
	}

	/**
	 * The latest attempt per language of the classifier that decided a Classification, made from its input hash, tells
	 * the versions it came from.
	 */
	@Test
	void reopensTheClassificationsThatCameFromAClassifierVersion(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory,
				product("feta", 1, "h1", Map.of("el", "Φέτα", "en", "Feta")),
				product("yoghurt", 2, "h2", Map.of("el", "Γιαούρτι")),
				product("honey", 3, "h3", Map.of("el", "Μέλι")),
				product("milk", 4, "h4", Map.of("el", "Γάλα")));
		var ids = findPending(sessionFactory, 10).stream().collect(Collectors.toMap(PendingProduct::getInputHash, PendingProduct::getId));
		var fixtures = new ArrayList<>();
		// v1 made the Greek attempt, v2 the English one
		fixtures.addAll(review(ids.get("h1"), classification(Classifier.LLM, YOGHURT, "h1", false, 1),
				attempt("el", Classifier.LLM, "v1", YOGHURT, "h1", 0), attempt("en", Classifier.LLM, "v2", YOGHURT, "h1", 1)));
		// v2 made the later Greek attempt
		fixtures.addAll(review(ids.get("h2"), classification(Classifier.LLM, YOGHURT, "h2", false, 1),
				attempt("el", Classifier.LLM, "v1", YOGHURT, "h2", 0), attempt("el", Classifier.LLM, "v2", YOGHURT, "h2", 1)));
		// an expert decided
		fixtures.addAll(review(ids.get("h3"), classification(Classifier.EXPERT, YOGHURT, "h3", false, 1),
				attempt("el", Classifier.LLM, "v1", YOGHURT, "h3", 0), attempt("el", Classifier.EXPERT, "v1", YOGHURT, "h3", 1)));
		// v1 classified the product before it changed
		fixtures.addAll(review(ids.get("h4"), classification(Classifier.LLM, YOGHURT, "h4", false, 1),
				attempt("el", Classifier.LLM, "v1", YOGHURT, "h0", 0), attempt("el", Classifier.LLM, "v2", YOGHURT, "h4", 1)));
		persist(sessionFactory, fixtures);

		var reopened = inTransaction(sessionFactory, tx -> new ClassificationDaoImpl().reopen(tx, Classifier.LLM, "v1"));

		assertEquals(1, reopened);
		assertEquals(List.of(ids.get("h1").asString()), rows("SELECT retailer_product_id FROM TH_CLASSIFICATION WHERE reopened"));
	}

	@Test
	void aReopenedProductIsPendingUntilItsClassificationIsSavedAgain(Mutiny.SessionFactory sessionFactory) throws SQLException {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")));
		var id = findPending(sessionFactory, 10).getFirst().getId();
		persist(sessionFactory, review(id, classification(Classifier.LLM, YOGHURT, "h1", false, 0), attempt("el", Classifier.LLM, "v1", YOGHURT, "h1", 0)));
		var dao = new ClassificationDaoImpl();

		inTransaction(sessionFactory, tx -> dao.reopen(tx, Classifier.LLM, "v1"));

		assertEquals(List.of(id), findPending(sessionFactory, 10).stream().map(PendingProduct::getId).toList());

		inTransaction(sessionFactory, tx -> dao.saveClassification(tx, id, classification(Classifier.LLM, YOGHURT, "h1", false, 1)));

		assertEquals(List.of(), findPending(sessionFactory, 10));
		assertEquals(List.of("false"), rows("SELECT reopened FROM TH_CLASSIFICATION"));
	}

	/**
	 * The product is pending again, and the Classification resolved from its next attempts decides whether it needs
	 * review.
	 */
	@Test
	void aReopenedClassificationToReviewIsNotFound(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι", "en", "Yoghurt")));
		var id = findPending(sessionFactory, 10).getFirst().getId();
		persist(sessionFactory, review(id, classification(Classifier.LLM, YOGHURT, "h1", true, 0),
				attempt("el", Classifier.LLM, "v1", YOGHURT, "h1", 0), attempt("en", Classifier.LLM, "v1", null, "h1", 0)));

		inTransaction(sessionFactory, tx -> new ClassificationDaoImpl().reopen(tx, Classifier.LLM, "v1"));

		assertEquals(List.of(), findToReview(sessionFactory, 10));
	}

	@Test
	void findsTheProductsOfTheCataloguesRetailerByKeyWithTheirClassifications(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory,
				product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")),
				product("feta", 2, "h2", Map.of("el", "Φέτα")),
				product("honey", 3, "h3", Map.of("el", "Μέλι")));
		var ids = idsByInputHash(sessionFactory);
		var yoghurt = classification(YOGHURT, "h1", true, 0);
		var noArchetype = classification(null, "h3", false, 0);
		var fixtures = new ArrayList<>();
		fixtures.addAll(review(ids.get("h1"), yoghurt, attempt("el", Classifier.EXPERT, YOGHURT, "h1", 0)));
		fixtures.addAll(review(ids.get("h3"), noArchetype, attempt("el", Classifier.EXPERT, null, "h3", 0)));
		persist(sessionFactory, fixtures);

		var products = findProductsToAssess(sessionFactory, CATALOGUE, "yoghurt", "feta", "honey", "unknown");

		assertEquals(Set.of(
				productToAssess(ids.get("h1"), "yoghurt", yoghurt, false),
				productToAssess(ids.get("h2"), "feta", null, true),
				productToAssess(ids.get("h3"), "honey", noArchetype, false)
		), Set.copyOf(products));
	}

	@Test
	void findsAProductToAssessWhetherItsListingIsInactiveOrInAnotherCatalogue(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory,
				listedIn(product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")), listing(CATALOGUE, false)),
				listedIn(product("feta", 2, "h2", Map.of("el", "Φέτα")), listing(CATALOGUE_CY, true)));

		var products = findProductsToAssess(sessionFactory, CATALOGUE, "yoghurt", "feta");

		assertEquals(Set.of("yoghurt", "feta"), products.stream().map(product -> product.getProductKey().asString()).collect(Collectors.toSet()));
	}

	@Test
	void aProductOfAnotherRetailerIsNotFoundToAssess(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, ImmutableProductChange.builder()
				.from(listedIn(product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")), listing(OTHER_RETAILERS_CATALOGUE, true)))
				.retailerId(new GenericRetailerId(OTHER_RETAILER.toString()))
				.build());

		assertEquals(List.of(), findProductsToAssess(sessionFactory, CATALOGUE, "yoghurt"));
	}

	@Test
	void anUnknownCatalogueHasNoProductsToAssess(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")));

		assertEquals(List.of(), findProductsToAssess(sessionFactory, "no-such-catalogue", "yoghurt"));
	}

	@Test
	void noKeysFindNoProductsToAssess(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")));

		assertEquals(List.of(), findProductsToAssess(sessionFactory, CATALOGUE));
	}

	/**
	 * The product keeps the Classification it had, but waits to be classified again.
	 */
	@Test
	void aProductToAssessThatChangedOrWhoseClassificationWasReopenedIsPending(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory,
				product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")),
				product("feta", 2, "h2", Map.of("el", "Φέτα")));
		var ids = idsByInputHash(sessionFactory);
		var yoghurt = classification(Classifier.LLM, YOGHURT, "h1", false, 0);
		var feta = classification(Classifier.LLM, YOGHURT, "h2", false, 0);
		var fixtures = new ArrayList<>();
		fixtures.addAll(review(ids.get("h1"), yoghurt, attempt("el", Classifier.LLM, "v1", YOGHURT, "h1", 0)));
		fixtures.addAll(review(ids.get("h2"), feta, attempt("el", Classifier.LLM, "v2", YOGHURT, "h2", 0)));
		persist(sessionFactory, fixtures);

		apply(sessionFactory, product("yoghurt", 3, "h3", Map.of("el", "Γιαούρτι στραγγιστό")));
		inTransaction(sessionFactory, tx -> new ClassificationDaoImpl().reopen(tx, Classifier.LLM, "v2"));

		var products = findProductsToAssess(sessionFactory, CATALOGUE, "yoghurt", "feta");

		assertEquals(Set.of(
				productToAssess(ids.get("h1"), "yoghurt", yoghurt, true),
				productToAssess(ids.get("h2"), "feta", feta, true)
		), Set.copyOf(products));
	}

	/**
	 * As many products as a batch assessment may name.
	 */
	@Test
	void findsManyProductsToAssessAtOnce(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory, IntStream.rangeClosed(1, 1000)
				.mapToObj(i -> product("product-" + i, i, "h" + i, Map.of("el", "Προϊόν " + i)))
				.toArray(ProductChange[]::new));

		var products = findProductsToAssess(sessionFactory, CATALOGUE, IntStream.rangeClosed(1, 1000).mapToObj(i -> "product-" + i).toArray(String[]::new));

		assertEquals(1000, products.size());
	}

	@Test
	void theCatalogueStocksTheArchetypesOfItsClassifiedProductsActivelyListed(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory,
				product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")),
				product("kefir", 2, "h2", Map.of("el", "Κεφίρ")),
				product("honey", 3, "h3", Map.of("el", "Μέλι")),
				product("feta", 4, "h4", Map.of("el", "Φέτα")));
		var ids = idsByInputHash(sessionFactory);
		var fixtures = new ArrayList<>();
		fixtures.addAll(review(ids.get("h1"), classification(YOGHURT, "h1", false, 0)));
		fixtures.addAll(review(ids.get("h2"), classification(KEFIR, "h2", false, 0)));
		fixtures.addAll(review(ids.get("h3"), classification(null, "h3", false, 0)));
		persist(sessionFactory, fixtures);

		assertEquals(Set.of(YOGHURT, KEFIR), findStockedArchetypes(sessionFactory, CATALOGUE));
	}

	@Test
	void anArchetypeListedOnlyInactiveOrInAnotherCatalogueIsNotStocked(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory,
				listedIn(product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")), listing(CATALOGUE, false)),
				listedIn(product("kefir", 2, "h2", Map.of("el", "Κεφίρ")), listing(CATALOGUE_CY, true)));
		var ids = idsByInputHash(sessionFactory);
		var fixtures = new ArrayList<>();
		fixtures.addAll(review(ids.get("h1"), classification(YOGHURT, "h1", false, 0)));
		fixtures.addAll(review(ids.get("h2"), classification(KEFIR, "h2", false, 0)));
		persist(sessionFactory, fixtures);

		assertEquals(Set.of(), findStockedArchetypes(sessionFactory, CATALOGUE));
		assertEquals(Set.of(KEFIR), findStockedArchetypes(sessionFactory, CATALOGUE_CY));
	}

	/**
	 * Whatever state its Classification is in, a product names the archetype it was last classified to.
	 */
	@Test
	void theClassificationOfAProductChangedSinceOrToReviewStocksItsArchetype(Mutiny.SessionFactory sessionFactory) {
		apply(sessionFactory,
				product("yoghurt", 1, "h1", Map.of("el", "Γιαούρτι")),
				product("kefir", 2, "h2", Map.of("el", "Κεφίρ")));
		var ids = idsByInputHash(sessionFactory);
		var fixtures = new ArrayList<>();
		fixtures.addAll(review(ids.get("h1"), classification(YOGHURT, "h1", false, 0)));
		fixtures.addAll(review(ids.get("h2"), classification(KEFIR, "h2", true, 0)));
		persist(sessionFactory, fixtures);
		apply(sessionFactory, product("yoghurt", 3, "h3", Map.of("el", "Γιαούρτι στραγγιστό")));

		assertEquals(Set.of(YOGHURT, KEFIR), findStockedArchetypes(sessionFactory, CATALOGUE));
	}

	private static Set<ArchetypeProductId> findStockedArchetypes(Mutiny.SessionFactory sessionFactory, String catalogueId) {
		var sut = new ClassificationDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.findStockedArchetypes(em, new CatalogueIdImpl(catalogueId)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static List<ProductToAssess> findProductsToAssess(Mutiny.SessionFactory sessionFactory, String catalogueId, String... productKeys) {
		var sut = new ClassificationDaoImpl();
		Set<ProductKey> keys = Arrays.stream(productKeys).map(ProductKeyImpl::new).collect(Collectors.toSet());
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.findProductsToAssess(em, new CatalogueIdImpl(catalogueId), keys))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static ProductToAssess productToAssess(RetailerProductId id, String productKey, Classification classification, boolean pending) {
		return ImmutableProductToAssess.builder()
				.id(id)
				.productKey(new ProductKeyImpl(productKey))
				.classification(classification)
				.isPending(pending)
				.build();
	}

	private static Map<String, RetailerProductId> idsByInputHash(Mutiny.SessionFactory sessionFactory) {
		return findPending(sessionFactory, 10).stream().collect(Collectors.toMap(PendingProduct::getInputHash, PendingProduct::getId));
	}

	private static List<PendingProduct> findPending(Mutiny.SessionFactory sessionFactory, int limit) {
		var sut = new ClassificationDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.findPending(em, limit))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static List<ClassificationToReview> findToReview(Mutiny.SessionFactory sessionFactory, int limit) {
		var sut = new ClassificationDaoImpl();
		return new ReactivePersistenceContextFactoryImpl(sessionFactory)
				.withoutTransaction(em -> sut.findToReview(em, limit))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	/**
	 * @return The entities of the Classification of the product and of its attempts
	 */
	private static List<Object> review(RetailerProductId id, Classification classification, ClassificationAttempt... attempts) {
		var entities = new ArrayList<>();
		for (var attempt : attempts) {
			var entity = new ClassificationAttemptEntity();
			entity.setId(UUID.randomUUID());
			entity.setRetailerProductId(id.asUuid());
			entity.setLang(attempt.getLanguage());
			entity.setClassifier(attempt.getClassifier());
			entity.setClassifierVersion(attempt.getClassifierVersion());
			entity.setArchetypeProductId(attempt.getArchetypeProductId() != null ? attempt.getArchetypeProductId().asUuid() : null);
			entity.setInputHash(attempt.getInputHash());
			entity.setCreatedAt(attempt.getCreatedAt());
			entities.add(entity);
		}
		var entity = new ClassificationEntity();
		entity.setRetailerProductId(id.asUuid());
		entity.setClassifier(classification.getClassifier());
		entity.setArchetypeProductId(classification.getArchetypeProductId() != null ? classification.getArchetypeProductId().asUuid() : null);
		entity.setInputHash(classification.getInputHash());
		entity.setNeedsReview(classification.isNeedsReview());
		entity.setResolvedAt(classification.getResolvedAt());
		entities.add(entity);
		return entities;
	}

	private static void persist(Mutiny.SessionFactory sessionFactory, List<Object> entities) {
		sessionFactory.withTransaction(session -> session.persistAll(entities.toArray())).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
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
		classification.setClassifier(Classifier.MANUAL);
		classification.setInputHash(inputHash);
		classification.setNeedsReview(false);
		classification.setResolvedAt(now);
		sessionFactory.withTransaction(session -> session.persistAll(attempt, classification)).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static <T> T inTransaction(Mutiny.SessionFactory sessionFactory, Function<ReactivePersistenceTxContext, Uni<T>> work) {
		return new ReactivePersistenceContextFactoryImpl(sessionFactory).withTransaction(work).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static ClassificationAttempt attempt(String language, Classifier classifier, ArchetypeProductId archetypeProductId, String inputHash, int minutesLater) {
		return attempt(language, classifier, archetypeProductId, inputHash, minutesLater, null);
	}

	private static ClassificationAttempt attempt(String language, Classifier classifier, ArchetypeProductId archetypeProductId, String inputHash, int minutesLater, MatchDiagnostics diagnostics) {
		return attempt(language, classifier, "test", archetypeProductId, inputHash, minutesLater, diagnostics);
	}

	private static ClassificationAttempt attempt(String language, Classifier classifier, String classifierVersion, ArchetypeProductId archetypeProductId, String inputHash, int minutesLater) {
		return attempt(language, classifier, classifierVersion, archetypeProductId, inputHash, minutesLater, null);
	}

	private static ClassificationAttempt attempt(String language, Classifier classifier, String classifierVersion, ArchetypeProductId archetypeProductId, String inputHash, int minutesLater, MatchDiagnostics diagnostics) {
		return ImmutableClassificationAttempt.builder()
				.language(language)
				.classifier(classifier)
				.classifierVersion(classifierVersion)
				.archetypeProductId(archetypeProductId)
				.inputHash(inputHash)
				.createdAt(T0.plusMinutes(minutesLater))
				.diagnostics(diagnostics)
				.build();
	}

	private static MatchDiagnostics diagnostics(String l1Category, String l2Category, String l3Category, String archetypeProduct) {
		return ImmutableMatchDiagnostics.builder()
				.l1Category(l1Category)
				.l2Category(l2Category)
				.l3Category(l3Category)
				.archetypeProduct(archetypeProduct)
				.build();
	}

	private static Classification classification(ArchetypeProductId archetypeProductId, String inputHash, boolean needsReview, int minutesLater) {
		return classification(Classifier.EXPERT, archetypeProductId, inputHash, needsReview, minutesLater);
	}

	private static Classification classification(Classifier classifier, ArchetypeProductId archetypeProductId, String inputHash, boolean needsReview, int minutesLater) {
		return ImmutableClassification.builder()
				.classifier(classifier)
				.archetypeProductId(archetypeProductId)
				.inputHash(inputHash)
				.isNeedsReview(needsReview)
				.resolvedAt(T0.plusMinutes(minutesLater))
				.build();
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

	private static ProductChange listedIn(ProductChange change, Listing listing) {
		return ImmutableProductChange.builder().from(change).listings(List.of(listing)).build();
	}

	private static Listing listing(String catalogueId, boolean active) {
		return ImmutableListing.builder()
				.catalogueId(new CatalogueIdImpl(catalogueId))
				.price(new BigDecimal("1.99"))
				.currency(Currency.getInstance("EUR"))
				.isActive(active)
				.build();
	}

	private static CatalogueEntity catalogue(String id, UUID retailerId, String country) {
		var catalogue = new CatalogueEntity();
		catalogue.setId(id);
		catalogue.setRetailerId(retailerId);
		catalogue.setCountry(country);
		catalogue.setPrimaryLanguage("el");
		catalogue.setActive(true);
		return catalogue;
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

	private static void lockWithoutWaiting(RetailerProductId id) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
				var s = c.prepareStatement("SELECT id FROM TH_RETAILER_PRODUCT WHERE id = ? FOR UPDATE NOWAIT")) {
			s.setObject(1, id.asUuid());
			s.executeQuery().close();
		}
	}

	/**
	 * @return Each row, its columns joined by {@code |}
	 */
	private static List<String> rows(String sql) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			var rows = new ArrayList<String>();
			var columns = rs.getMetaData().getColumnCount();
			while (rs.next()) {
				var row = new StringJoiner("|");
				for (int i = 1; i <= columns; i++) {
					var value = rs.getObject(i);
					row.add(value instanceof Timestamp timestamp ? timestamp.toLocalDateTime().toString() : String.valueOf(value));
				}
				rows.add(row.toString());
			}
			return rows;
		}
	}
}
