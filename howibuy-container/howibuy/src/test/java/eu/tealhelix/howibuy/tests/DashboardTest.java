package eu.tealhelix.howibuy.tests;

import static eu.tealhelix.howibuy.tests.AssessmentFixtures.BASE;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.CATALOGUE_GR;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.RETAILER_ID;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.as;
import static eu.tealhelix.howibuy.tests.AssessmentFixtures.listing;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.common.test.quarkus.PostgresTestResource;
import eu.tealhelix.common.v1.model.Role;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.jaxrs.authentication.jwt.Authentication;
import eu.tealhelix.howibuy.jaxrs.authentication.jwt.TokenHelper;
import eu.tealhelix.howibuy.scoring.v1.ScoredProduct;
import eu.tealhelix.howibuy.services.v1.ai.ProductAssessmentAiFacade;
import eu.tealhelix.howibuy.services.v1.basket.BadgeRules;
import eu.tealhelix.howibuy.services.v1.basket.BasketRules;
import eu.tealhelix.howibuy.services.v1.impl.ArchetypeCorpus;
import eu.tealhelix.howibuy.v1.types.Badge;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The dashboard an end-user reads with their own token, against a real database. The Retailer registers the purchases
 * as in {@link BasketAssessmentTest}, with the products brought in and classified as there. The expected averages and
 * counts are worked out from the Normalised Scores of the seed archetypes, so they hold whatever those scores are.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, initArgs = @ResourceArg(name = "contexts", value = "appdata"))
public class DashboardTest {
	private static final String SHOPPER_ID = "8a3c5e71-2d4b-4f69-b1e8-7c0d9a2f5b13";
	private static final User SHOPPER = new UserImpl(new UserIdImpl(SHOPPER_ID), "dashboard-shopper", null, false, true, Set.of());
	private static final String SHOPPER_CORRELATION_ID = "dashboard-shopper";
	private static final String NEWCOMER_ID = "3e9b7d25-6c1a-4b8f-a2d4-5f0e8c3b9a71";
	private static final User NEWCOMER = new UserImpl(new UserIdImpl(NEWCOMER_ID), "dashboard-newcomer", null, false, true, Set.of());
	private static final User RETAILER = new UserImpl(new UserIdImpl(RETAILER_ID), "test-retailer", null, false, false, Set.of(Role.RETAILER));

	private static final Map<String, Authentication> AUTHENTICATIONS_BY_TOKEN = AssessmentFixtures.authenticationsWith(Map.of(
			"shopper", new Authentication(SHOPPER, null),
			"newcomer", new Authentication(NEWCOMER, null),
			"retailer", new Authentication(RETAILER, null)));

	private static final BasketRules RULES = new BasketRules();
	private static final BadgeRules BADGE_RULES = new BadgeRules();

	@Inject
	DataSource dataSource;

	@Inject
	ArchetypeCorpus archetypeCorpus;

	private AssessmentFixtures fixtures;

	@BeforeEach
	void setUp() throws SQLException {
		QuarkusMock.installMockForType(new StubTokenHelper(AUTHENTICATIONS_BY_TOKEN), TokenHelper.class);
		QuarkusMock.installMockForType(new UnreachableProductAssessmentAiFacade(), ProductAssessmentAiFacade.class);
		fixtures = new AssessmentFixtures(dataSource);
		fixtures.reset();
		try (var c = dataSource.getConnection(); var s = c.createStatement()) {
			s.execute("DELETE FROM TH_USER_BADGE");
			s.execute("DELETE FROM TH_PURCHASED_BASKET_PRODUCT");
			s.execute("DELETE FROM TH_PURCHASED_BASKET");
			for (var userId : List.of(SHOPPER_ID, NEWCOMER_ID)) {
				s.execute("INSERT INTO TH_USER_PROFILE (id) VALUES ('" + userId + "') ON CONFLICT (id) DO NOTHING");
				s.execute("UPDATE TH_USER_PROFILE SET xp_total = 0, streak = 0 WHERE id = '" + userId + "'");
			}
			s.execute("INSERT INTO TH_CORREL_ID (retailer_id, correlation_id, user_id) VALUES ('" + RETAILER_ID + "', '" + SHOPPER_CORRELATION_ID + "', '" + SHOPPER_ID + "') ON CONFLICT DO NOTHING");
			s.execute("INSERT INTO TH_CONSENT (user_id, retailer_id, state) VALUES ('" + SHOPPER_ID + "', '" + RETAILER_ID + "', 'GRANTED') ON CONFLICT DO NOTHING");
		}
	}

	/**
	 * Both purchases are made on October 31st in UTC, but the shop is two hours ahead, where the second one is made in
	 * November. Each month holds one of the two products, so its averages are that product's Normalised Scores.
	 */
	@Test
	void aPurchaseCountsInTheMonthOfItsTimeAtTheShop() throws SQLException {
		var scoredArchetypes = scoredSeedArchetypes();
		var october = scoredArchetypes.get(0);
		var november = scoredArchetypes.get(1);
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.applyProduct("feta", 2, "h2", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", october.productId().asString());
		fixtures.classify("feta", "h2", november.productId().asString());
		purchase("2026-10-31T23:30:00+02:00", "yoghurt");
		purchase("2026-11-01T00:30:00+02:00", "feta");

		var dashboard = dashboard("shopper", "2026-11");

		assertEquals(
				perDimension(dimension -> rounded(november, dimension) + "|" + rounded(october, dimension)),
				perEntry(dashboard, "monthlyProgress", progress -> progress.get("currentAverage") + "|" + progress.get("previousAverage")),
				"the purchase at 00:30 at the shop counts in November, the one at 23:30 in October");
		assertEquals(xpTotal(), dashboard.getInt("xpTotal"));
		assertEquals(streak(), dashboard.getInt("streak"));
		assertEquals(RULES.xpPerProduct(), dashboard.getInt("xpPerProduct"));
		assertEquals(2, dashboard.getInt("purchasedBasketCount"));
		assertNull(dashboard.get("xpPercentile"), "no other user has bought anything");
		assertEquals(
				perDimension(dimension -> String.valueOf(Stream.of(october, november).filter(product -> isAboveTheBadgeThreshold(product, dimension)).count())),
				perEntry(dashboard, "badgeSeries", series -> String.valueOf(series.get("productCount"))));
		assertEquals(
				Map.of("badge", "ENVIRONMENTAL_HERO_1", "label", "Environmental Hero Level 1",
						"requiredProducts", BADGE_RULES.requirementOf(Badge.ENVIRONMENTAL_HERO_1).products(), "earned", false),
				dashboard.getMap("badgeSeries[0].levels[0]"));
	}

	/**
	 * Another user has bought something, so the newcomer has other shoppers, though not one basket.
	 */
	@Test
	void theDashboardOfANewUserIsEmpty() throws SQLException {
		var product = scoredSeedArchetypes().getFirst();
		fixtures.applyProduct("yoghurt", 1, "h1", listing(CATALOGUE_GR, true));
		fixtures.classify("yoghurt", "h1", product.productId().asString());
		purchase("2026-11-06T18:30:00+02:00", "yoghurt");

		var dashboard = dashboard("newcomer", "2026-11");

		assertEquals(0, dashboard.getInt("xpTotal"));
		assertEquals(0, dashboard.getInt("streak"));
		assertEquals(0, dashboard.getInt("purchasedBasketCount"));
		assertNull(dashboard.get("xpPercentile"), "without a basket, the newcomer is not among the shoppers");
		assertEquals(perDimension(_ -> "null|null|null"),
				perEntry(dashboard, "monthlyProgress", progress -> progress.get("currentAverage") + "|" + progress.get("previousAverage") + "|" + progress.get("changePercent")));
		assertEquals(perDimension(_ -> "0|[false, false, false]"), perEntry(dashboard, "badgeSeries", DashboardTest::countAndEarned));
	}

	@Test
	void aMonthIsRequired() {
		as("shopper").get(BASE + "/dashboard").then()
				.statusCode(400)
				.body("message", equalTo("Required input 'month' is missing."));
	}

	@Test
	void anEmptyMonthIsMissing() {
		read("shopper", "").statusCode(400).body("message", equalTo("Required input 'month' is missing."));
	}

	@ParameterizedTest
	@ValueSource(strings = {"2026-13", "2026-1", "11-2026", "2026-11-01"})
	void aMalformedMonthIsRefused(String month) {
		read("shopper", month).statusCode(400).body("message", equalTo("The value of input 'month' is illegal."));
	}

	@Test
	void aServiceAccountIsForbidden() {
		read("retailer", "2026-11").statusCode(403);
	}

	/**
	 * The seed archetypes that have an Overall Score, so that a product classified as one of them is stored with a
	 * purchase.
	 */
	private List<ScoredProduct> scoredSeedArchetypes() throws SQLException {
		var scoredArchetypes = archetypeCorpus.scoredArchetypes().await().atMost(Duration.ofSeconds(60));
		return fixtures.rows("SELECT id::text FROM TH_ARCHETYPE_PRODUCT ORDER BY id").stream()
				.flatMap(id -> scoredArchetypes.find(new ArchetypeProductIdImpl(id)).stream())
				.toList();
	}

	/**
	 * The Normalised Score of the product in the dimension, rounded as the dashboard rounds an average: two decimals,
	 * half up.
	 */
	private static Double rounded(ScoredProduct product, ScoredSustainabilityDimension dimension) {
		return BigDecimal.valueOf(product.normalisedScores().get(dimension)).setScale(2, RoundingMode.HALF_UP).doubleValue();
	}

	private static boolean isAboveTheBadgeThreshold(ScoredProduct product, ScoredSustainabilityDimension dimension) {
		return product.normalisedScores().get(dimension) > BADGE_RULES.normalisedScoreThreshold();
	}

	private static String countAndEarned(Map<String, Object> series) {
		@SuppressWarnings("unchecked")
		var levels = (List<Map<String, Object>>) series.get("levels");
		return series.get("productCount") + "|" + levels.stream().map(level -> level.get("earned")).toList();
	}

	/**
	 * @return The value of every dimension, in the order of {@link ScoredSustainabilityDimension}, after its name
	 */
	private static List<String> perDimension(Function<ScoredSustainabilityDimension, String> value) {
		return Arrays.stream(ScoredSustainabilityDimension.values()).map(dimension -> dimension + "|" + value.apply(dimension)).toList();
	}

	/**
	 * @return The value of every entry of a list of the dashboard, after the name of its dimension
	 */
	private static List<String> perEntry(JsonPath dashboard, String list, Function<Map<String, Object>, String> value) {
		List<Map<String, Object>> entries = dashboard.getList(list);
		return entries.stream().map(entry -> entry.get("dimension") + "|" + value.apply(entry)).toList();
	}

	private int xpTotal() throws SQLException {
		return Integer.parseInt(fixtures.rows("SELECT xp_total FROM TH_USER_PROFILE WHERE id = '" + SHOPPER_ID + "'").getFirst());
	}

	private int streak() throws SQLException {
		return Integer.parseInt(fixtures.rows("SELECT streak FROM TH_USER_PROFILE WHERE id = '" + SHOPPER_ID + "'").getFirst());
	}

	/**
	 * The Retailer registers the shopper's purchase of the products.
	 */
	private static void purchase(String purchasedAt, String... productKeys) {
		var request = Map.of(
				"correlationId", SHOPPER_CORRELATION_ID,
				"catalogueId", CATALOGUE_GR,
				"purchasedAt", purchasedAt,
				"language", "en",
				"productKeys", List.of(productKeys));
		as("retailer").contentType(JSON).body(request).post(BASE + "/assessment/basket/purchase").then().statusCode(200);
	}

	/**
	 * @return The dashboard, its decimals read as {@code double}s, as the application writes them
	 */
	private static JsonPath dashboard(String token, String month) {
		return read(token, month).statusCode(200).contentType(JSON).extract().jsonPath()
				.using(new JsonPathConfig(JsonPathConfig.NumberReturnType.DOUBLE));
	}

	private static ValidatableResponse read(String token, String month) {
		return as(token).queryParam("month", month).get(BASE + "/dashboard").then();
	}
}
