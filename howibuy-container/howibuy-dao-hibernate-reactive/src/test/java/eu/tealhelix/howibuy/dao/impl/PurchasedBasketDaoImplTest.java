package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.tealhelix.howibuy.dao.impl.TestEntities.catalogue;
import static eu.tealhelix.howibuy.dao.impl.TestEntities.retailer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity;
import eu.tealhelix.howibuy.services.model.ImmutablePurchasedBasket;
import eu.tealhelix.howibuy.services.model.ImmutablePurchasedBasketProduct;
import eu.tealhelix.howibuy.services.model.PurchasedBasket;
import eu.tealhelix.howibuy.services.model.PurchasedBasketProduct;
import eu.tealhelix.howibuy.v1.types.BasketAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.BasketGrade;
import eu.tealhelix.howibuy.v1.types.ProductGrade;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A Purchased Basket is stored with the products that have an Overall Score, and the user's XP total grows in the same
 * transaction.
 * <p>
 * The stored rows are read back with JDBC: the test is about the mapping of the rows, which reading them through the
 * same mapping could not check.
 */
@Testcontainers
public class PurchasedBasketDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID RETAILER = UUID.fromString("4a7c1e93-2b5d-4f80-9c36-e1d2a8b07f45");
	private static final String CATALOGUE = "test-lt";
	private static final UUID USER = UUID.fromString("c3e85f1a-7d24-4b96-a0f3-58b9e6d21c07");
	private static final UUID ATTEMPT = UUID.fromString("0e6d9b42-1f87-4c3a-b5e0-7a29c4f8d613");
	private static final UUID MILK = UUID.fromString("7b2f4e18-9c63-4a05-8d71-e4a0b3c95f26");
	private static final UUID CHIPS = UUID.fromString("7b2f4e18-9c63-4a05-8d71-e4a0b3c95f27");
	private static final LocalDateTime PURCHASED_AT = LocalDateTime.of(2026, 10, 31, 9, 15, 30);
	private static final LocalDateTime RECORDED_AT = LocalDateTime.of(2026, 10, 31, 9, 20, 45);

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
	static void setUp(Mutiny.SessionFactory sessionFactory) {
		var user = new UserProfileEntity();
		user.setId(USER);
		sessionFactory.withTransaction(session -> session.persistAll(retailer(RETAILER, "Test Retailer"), catalogue(CATALOGUE, RETAILER, "LT", "lt"), user))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	@BeforeEach
	void deleteBaskets() throws SQLException {
		execute("DELETE FROM TH_PURCHASED_BASKET_PRODUCT", "DELETE FROM TH_PURCHASED_BASKET");
	}

	@Test
	void storesTheBasketWithItsProducts(Mutiny.SessionFactory sessionFactory) throws SQLException {
		var sut = new PurchasedBasketDaoImpl();
		var basket = basket(BasketAssessmentOutcomeType.ASSESSED, BasketGrade.B, 4, ATTEMPT, List.of(
				product("milk-1", MILK, 0.85, ProductGrade.A, 0.9, 0.8, 0.7, 0.95),
				product("chips-2", CHIPS, 0.65, ProductGrade.B, 0.6, 0.5, 0.75, 0.4)));

		new ReactivePersistenceContextFactoryImpl(sessionFactory).withTransaction(tx -> sut.store(tx, basket))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var baskets = rows("SELECT id, user_profile_id, retailer_id, catalogue_id, purchased_at, recorded_at, outcome_type, basket_grade, xp_value, compass_attempt_id FROM TH_PURCHASED_BASKET");
		assertEquals(1, baskets.size());
		var stored = baskets.getFirst();
		assertEquals(USER, stored.get("user_profile_id"));
		assertEquals(RETAILER, stored.get("retailer_id"));
		assertEquals(CATALOGUE, stored.get("catalogue_id"));
		assertEquals(PURCHASED_AT, stored.get("purchased_at"));
		assertEquals(RECORDED_AT, stored.get("recorded_at"));
		assertEquals("ASSESSED", stored.get("outcome_type"));
		assertEquals("B", stored.get("basket_grade"));
		assertEquals(4, stored.get("xp_value"));
		assertEquals(ATTEMPT, stored.get("compass_attempt_id"));

		var products = rows("SELECT purchased_basket_id, product_key, archetype_product_id, overall_score, product_grade, score_environment, score_animal_welfare, score_socio_economic, score_health FROM TH_PURCHASED_BASKET_PRODUCT ORDER BY product_key");
		assertEquals(2, products.size());
		var chips = products.get(0);
		assertEquals(stored.get("id"), chips.get("purchased_basket_id"));
		assertEquals("chips-2", chips.get("product_key"));
		assertEquals(CHIPS, chips.get("archetype_product_id"));
		assertEquals(0.65, chips.get("overall_score"));
		assertEquals("B", chips.get("product_grade"));
		assertEquals(0.6, chips.get("score_environment"));
		assertEquals(0.5, chips.get("score_animal_welfare"));
		assertEquals(0.75, chips.get("score_socio_economic"));
		assertEquals(0.4, chips.get("score_health"));
		var milk = products.get(1);
		assertEquals(stored.get("id"), milk.get("purchased_basket_id"));
		assertEquals("milk-1", milk.get("product_key"));
		assertEquals(MILK, milk.get("archetype_product_id"));
		assertEquals(0.85, milk.get("overall_score"));
		assertEquals("A", milk.get("product_grade"));
		assertEquals(0.9, milk.get("score_environment"));
		assertEquals(0.8, milk.get("score_animal_welfare"));
		assertEquals(0.7, milk.get("score_socio_economic"));
		assertEquals(0.95, milk.get("score_health"));
	}

	@Test
	void storesABasketWithNothingToAssessWithoutProducts(Mutiny.SessionFactory sessionFactory) throws SQLException {
		var sut = new PurchasedBasketDaoImpl();
		var basket = basket(BasketAssessmentOutcomeType.NOTHING_TO_ASSESS, null, 0, null, List.of());

		new ReactivePersistenceContextFactoryImpl(sessionFactory).withTransaction(tx -> sut.store(tx, basket))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var baskets = rows("SELECT outcome_type, basket_grade, xp_value, compass_attempt_id FROM TH_PURCHASED_BASKET");
		assertEquals(1, baskets.size());
		var stored = baskets.getFirst();
		assertEquals("NOTHING_TO_ASSESS", stored.get("outcome_type"));
		assertNull(stored.get("basket_grade"));
		assertEquals(0, stored.get("xp_value"));
		assertNull(stored.get("compass_attempt_id"));
		assertEquals(0, rows("SELECT product_key FROM TH_PURCHASED_BASKET_PRODUCT").size());
	}

	@Test
	void storesEveryBasketItIsGiven(Mutiny.SessionFactory sessionFactory) throws SQLException {
		var sut = new PurchasedBasketDaoImpl();
		var basket = basket(BasketAssessmentOutcomeType.ASSESSED, BasketGrade.A, 3, null, List.of(product("milk-1", MILK, 0.85, ProductGrade.A, 0.9, 0.8, 0.7, 0.95)));
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.store(tx, basket)).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.store(tx, basket)).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(2, rows("SELECT id FROM TH_PURCHASED_BASKET").size());
		assertEquals(2, rows("SELECT product_key FROM TH_PURCHASED_BASKET_PRODUCT").size());
	}

	@Test
	void storesABasketAsLargeAsTheLargestAllowed(Mutiny.SessionFactory sessionFactory) throws SQLException {
		var sut = new PurchasedBasketDaoImpl();
		var products = IntStream.range(0, 400)
				.mapToObj(i -> product("product-" + i, MILK, 0.85, ProductGrade.A, 0.9, 0.8, 0.7, 0.95))
				.toList();
		var basket = basket(BasketAssessmentOutcomeType.ASSESSED, BasketGrade.A, 1200, ATTEMPT, products);

		new ReactivePersistenceContextFactoryImpl(sessionFactory).withTransaction(tx -> sut.store(tx, basket))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(400, rows("SELECT product_key FROM TH_PURCHASED_BASKET_PRODUCT").size());
	}

	@Test
	void storesTheBasketAndAddsItsXpInOneTransaction(Mutiny.SessionFactory sessionFactory) throws SQLException {
		var sut = new PurchasedBasketDaoImpl();
		var userProfileDao = new UserProfileDaoImpl();
		var basket = basket(BasketAssessmentOutcomeType.ASSESSED, BasketGrade.A, 3, null, List.of(product("milk-1", MILK, 0.85, ProductGrade.A, 0.9, 0.8, 0.7, 0.95)));
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		Integer totalBefore = factory.withoutTransaction(em -> userProfileDao.requireXpTotal(em, userId())).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		Integer totalAfter = factory.withTransaction(tx -> sut.store(tx, basket)
						.chain(() -> userProfileDao.addXp(tx, userId(), basket.getXpValue()))
						.chain(() -> userProfileDao.requireXpTotal(tx, userId())))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertEquals(totalBefore + 3, totalAfter);
		assertEquals(1, rows("SELECT id FROM TH_PURCHASED_BASKET").size());
	}

	private static UserId userId() {
		return new UserIdImpl(USER.toString());
	}

	private static PurchasedBasket basket(BasketAssessmentOutcomeType type, BasketGrade grade, int xpValue, UUID attemptId, List<PurchasedBasketProduct> products) {
		return ImmutablePurchasedBasket.builder()
				.userId(userId())
				.retailerId(new GenericRetailerId(RETAILER.toString()))
				.catalogueId(new CatalogueIdImpl(CATALOGUE))
				.purchasedAt(PURCHASED_AT)
				.recordedAt(RECORDED_AT)
				.type(type)
				.basketGrade(grade)
				.xpValue(xpValue)
				.compassAttemptId(attemptId)
				.products(products)
				.build();
	}

	private static PurchasedBasketProduct product(String key, UUID archetypeId, double overallScore, ProductGrade grade, double environment, double animalWelfare, double socioEconomic, double health) {
		return ImmutablePurchasedBasketProduct.builder()
				.productKey(new ProductKeyImpl(key))
				.archetypeProductId(new ArchetypeProductIdImpl(archetypeId.toString()))
				.overallScore(overallScore)
				.productGrade(grade)
				.normalisedScores(Map.of(
						ScoredSustainabilityDimension.ENVIRONMENT, environment,
						ScoredSustainabilityDimension.ANIMAL_WELFARE, animalWelfare,
						ScoredSustainabilityDimension.SOCIO_ECONOMIC, socioEconomic,
						ScoredSustainabilityDimension.HEALTH, health))
				.build();
	}

	private static void execute(String... statements) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement()) {
			for (var statement : statements) {
				s.execute(statement);
			}
		}
	}

	/**
	 * @return The rows, each a map of its columns by their names; a timestamp as a {@code LocalDateTime}
	 */
	private static List<Map<String, Object>> rows(String sql) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			var rows = new ArrayList<Map<String, Object>>();
			var columnCount = rs.getMetaData().getColumnCount();
			while (rs.next()) {
				var row = new HashMap<String, Object>();
				for (int i = 1; i <= columnCount; i++) {
					var value = rs.getObject(i);
					row.put(rs.getMetaData().getColumnName(i), value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : value);
				}
				rows.add(row);
			}
			return rows;
		}
	}
}
