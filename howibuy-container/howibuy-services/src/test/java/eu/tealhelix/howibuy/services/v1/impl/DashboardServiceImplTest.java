package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.v1.types.UserIdTestUtils.matchesHasUserId;
import static eu.tealhelix.howibuy.v1.types.Badge.ANIMAL_WELFARE_HERO_1;
import static eu.tealhelix.howibuy.v1.types.Badge.ANIMAL_WELFARE_HERO_2;
import static eu.tealhelix.howibuy.v1.types.Badge.ANIMAL_WELFARE_HERO_3;
import static eu.tealhelix.howibuy.v1.types.Badge.ENVIRONMENTAL_HERO_1;
import static eu.tealhelix.howibuy.v1.types.Badge.ENVIRONMENTAL_HERO_2;
import static eu.tealhelix.howibuy.v1.types.Badge.ENVIRONMENTAL_HERO_3;
import static eu.tealhelix.howibuy.v1.types.Badge.HEALTH_HERO_1;
import static eu.tealhelix.howibuy.v1.types.Badge.HEALTH_HERO_2;
import static eu.tealhelix.howibuy.v1.types.Badge.HEALTH_HERO_3;
import static eu.tealhelix.howibuy.v1.types.Badge.SOCIO_ECONOMIC_HERO_1;
import static eu.tealhelix.howibuy.v1.types.Badge.SOCIO_ECONOMIC_HERO_2;
import static eu.tealhelix.howibuy.v1.types.Badge.SOCIO_ECONOMIC_HERO_3;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIO_ECONOMIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.YearMonth;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.PurchasedBasketDao;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import eu.tealhelix.howibuy.services.model.ImmutableXpAndStreak;
import eu.tealhelix.howibuy.services.model.ImmutableXpStanding;
import eu.tealhelix.howibuy.services.model.XpStanding;
import eu.tealhelix.howibuy.services.v1.basket.BadgeRules;
import eu.tealhelix.howibuy.services.v1.basket.BasketRules;
import eu.tealhelix.howibuy.v1.model.BadgeLevel;
import eu.tealhelix.howibuy.v1.model.BadgeSeries;
import eu.tealhelix.howibuy.v1.model.Dashboard;
import eu.tealhelix.howibuy.v1.model.DimensionProgress;
import eu.tealhelix.howibuy.v1.model.ImmutableDimensionProgress;
import eu.tealhelix.howibuy.v1.types.Badge;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The DAOs are mocked; the rules are the real {@code BasketRules} and {@code BadgeRules}, whose numbers the dashboard
 * reports. The test bundle {@code BadgeLabels_xx} labels the badges in a made-up language, {@code xx}.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final User USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, true, Set.of());
	private static final User SERVICE_USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, false, Set.of());
	private static final YearMonth MONTH = YearMonth.of(2026, 11);
	private static final YearMonth PREVIOUS_MONTH = YearMonth.of(2026, 10);
	private static final String LANGUAGE = "en";
	private static final int XP_TOTAL = 1440;
	private static final int STREAK = 8;
	private static final long BASKET_COUNT = 9;
	private static final Map<ScoredSustainabilityDimension, Long> NOTHING_COUNTED = counts(0, 0, 0, 0);
	private static final BadgeRules BADGE_RULES = new BadgeRules();

	@Mock
	UserProfileDao userProfileDao;

	@Mock
	PurchasedBasketDao purchasedBasketDao;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	private DashboardServiceImpl sut;

	@BeforeEach
	void setUp() {
		sut = new DashboardServiceImpl(new TealHelixAuthorizationImpl(), persistenceContextFactory, userProfileDao, purchasedBasketDao);
	}

	@Test
	void rejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.retrieveDashboard(SERVICE_USER, MONTH, LANGUAGE));
		verifyNoInteractions(userProfileDao, purchasedBasketDao);
	}

	@Test
	void requiresAMonth() {
		var e = assertThrows(RequiredInputMissingException.class, () -> sut.retrieveDashboard(USER, null, LANGUAGE));
		assertEquals("month", e.getInputName());
		verifyNoInteractions(userProfileDao, purchasedBasketDao);
	}

	@Test
	void reportsTheXpTheStreakAndTheBasketsOfTheUser() {
		givenAShopper();
		givenNoAverages();
		givenTheBadgeStanding(NOTHING_COUNTED, Set.of());

		var dashboard = retrieve(LANGUAGE);

		assertEquals(XP_TOTAL, dashboard.getXpTotal());
		assertEquals(STREAK, dashboard.getStreak());
		assertEquals(BASKET_COUNT, dashboard.getPurchasedBasketCount());
		assertEquals(new BasketRules().xpPerProduct(), dashboard.getXpPerProduct());
	}

	@Test
	void readsInOneContextWithoutATransaction() {
		givenAShopper();
		givenNoAverages();
		givenTheBadgeStanding(NOTHING_COUNTED, Set.of());

		retrieve(LANGUAGE);

		assertEquals(List.of(), persistenceContextFactory.getOpenedTransactions());
		var em = ArgumentCaptor.forClass(ReactivePersistenceContext.class);
		verify(userProfileDao).requireXpAndStreak(em.capture(), any());
		verify(userProfileDao).retrieveXpStanding(em.capture(), any());
		verify(purchasedBasketDao).countBaskets(em.capture(), any());
		verify(purchasedBasketDao, times(2)).retrieveAverageNormalisedScores(em.capture(), any(), any());
		verify(purchasedBasketDao).retrieveProductCountsAbove(em.capture(), any(), anyDouble());
		verify(purchasedBasketDao).findBadges(em.capture(), any());
		var contexts = new IdentityHashMap<ReactivePersistenceContext, Boolean>();
		em.getAllValues().forEach(context -> contexts.put(context, true));
		assertEquals(1, contexts.size(), "every read in the same context");
	}

	@Test
	void roundsThePercentileDown() {
		givenTheUser(BASKET_COUNT, standing(3, 2));
		givenNoAverages();
		givenTheBadgeStanding(NOTHING_COUNTED, Set.of());

		assertEquals(66, retrieve(LANGUAGE).getXpPercentile(), "2 behind of 3 other shoppers");
	}

	@Test
	void hasNoPercentileWithoutABasket() {
		givenTheUser(0, standing(5, 0));
		givenNoAverages();
		givenTheBadgeStanding(NOTHING_COUNTED, Set.of());

		assertNull(retrieve(LANGUAGE).getXpPercentile(), "not 0, though nobody is behind");
	}

	@Test
	void hasNoPercentileWithoutOtherShoppers() {
		givenTheUser(BASKET_COUNT, standing(0, 0));
		givenNoAverages();
		givenTheBadgeStanding(NOTHING_COUNTED, Set.of());

		assertNull(retrieve(LANGUAGE).getXpPercentile());
	}

	/**
	 * <pre>
	 *                   previous  current   rounded      change   from the averages unrounded
	 * environment       0.8449    0.8712    0.84  0.87   +3.6     +3.1
	 * animal welfare    0.596     0.604     0.60  0.60    0.0     +1.3
	 * socio-economic    0.80      0.81      0.80  0.81   +1.3     +1.25, rounded half up
	 * health            0.60      0.845     0.60  0.85   +41.7    0.845 rounded half up
	 * </pre>
	 */
	@Test
	void roundsTheAveragesAndComputesTheChangeFromTheRoundedOnes() {
		givenAShopper();
		givenTheAverages(averages(0.8712, 0.604, 0.81, 0.845), averages(0.8449, 0.596, 0.80, 0.60));
		givenTheBadgeStanding(NOTHING_COUNTED, Set.of());

		assertEquals(
				List.of(
						progress(ENVIRONMENT, 0.87, 0.84, 3.6),
						progress(ANIMAL_WELFARE, 0.60, 0.60, 0.0),
						progress(SOCIO_ECONOMIC, 0.81, 0.80, 1.3),
						progress(HEALTH, 0.85, 0.60, 41.7)),
				retrieve(LANGUAGE).getMonthlyProgress());
	}

	@Test
	void hasNoChangeFromAMonthWithoutPurchases() {
		givenAShopper();
		givenTheAverages(averages(0.87, 0.6, 0.5, 0.4), Optional.empty());
		givenTheBadgeStanding(NOTHING_COUNTED, Set.of());

		assertEquals(
				List.of(
						progress(ENVIRONMENT, 0.87, null, null),
						progress(ANIMAL_WELFARE, 0.6, null, null),
						progress(SOCIO_ECONOMIC, 0.5, null, null),
						progress(HEALTH, 0.4, null, null)),
				retrieve(LANGUAGE).getMonthlyProgress());
	}

	@Test
	void hasNoAveragesNorChangeInAMonthWithoutPurchases() {
		givenAShopper();
		givenTheAverages(Optional.empty(), averages(0.87, 0.6, 0.5, 0.4));
		givenTheBadgeStanding(NOTHING_COUNTED, Set.of());

		assertEquals(
				List.of(
						progress(ENVIRONMENT, null, 0.87, null),
						progress(ANIMAL_WELFARE, null, 0.6, null),
						progress(SOCIO_ECONOMIC, null, 0.5, null),
						progress(HEALTH, null, 0.4, null)),
				retrieve(LANGUAGE).getMonthlyProgress());
	}

	@Test
	void hasNoChangeFromAPreviousAverageOf0() {
		givenAShopper();
		givenTheAverages(averages(0.5, 0.5, 0.5, 0.5), averages(0.004, 0.0, 0.5, 0.5));
		givenTheBadgeStanding(NOTHING_COUNTED, Set.of());

		var progress = retrieve(LANGUAGE).getMonthlyProgress();

		assertEquals(progress(ENVIRONMENT, 0.5, 0.0, null), progress.get(0), "0.004 rounds to 0.00");
		assertEquals(progress(ANIMAL_WELFARE, 0.5, 0.0, null), progress.get(1));
	}

	@Test
	void earnsTheLevelsTheUserHoldsWhateverTheCount() {
		var environmentCount = productsOf(ENVIRONMENTAL_HERO_3) - 1;
		var animalWelfareCount = productsOf(ANIMAL_WELFARE_HERO_2);
		givenAShopper();
		givenNoAverages();
		givenTheBadgeStanding(
				counts(environmentCount, animalWelfareCount, 0, 0),
				Set.of(ENVIRONMENTAL_HERO_1, ENVIRONMENTAL_HERO_2, ENVIRONMENTAL_HERO_3, ANIMAL_WELFARE_HERO_1));

		var dashboard = retrieve(LANGUAGE);

		var environment = seriesOf(dashboard, ENVIRONMENT);
		assertEquals(environmentCount, environment.getProductCount());
		assertEquals(List.of(true, true, true), earned(environment), "the third level is held, though its number is now above the count");
		var animalWelfare = seriesOf(dashboard, ANIMAL_WELFARE);
		assertEquals(animalWelfareCount, animalWelfare.getProductCount());
		assertEquals(List.of(true, false, false), earned(animalWelfare), "the count reaches the second level, but the user does not hold it");
	}

	@Test
	void ordersTheSeriesByDimensionAndTheLevelsByBadge() {
		givenAShopper();
		givenNoAverages();
		givenTheBadgeStanding(NOTHING_COUNTED, Set.of());

		var series = retrieve(LANGUAGE).getBadgeSeries();

		assertEquals(List.of(ScoredSustainabilityDimension.values()), series.stream().map(BadgeSeries::getDimension).toList());
		assertEquals(
				List.of(
						List.of(ENVIRONMENTAL_HERO_1, ENVIRONMENTAL_HERO_2, ENVIRONMENTAL_HERO_3),
						List.of(ANIMAL_WELFARE_HERO_1, ANIMAL_WELFARE_HERO_2, ANIMAL_WELFARE_HERO_3),
						List.of(SOCIO_ECONOMIC_HERO_1, SOCIO_ECONOMIC_HERO_2, SOCIO_ECONOMIC_HERO_3),
						List.of(HEALTH_HERO_1, HEALTH_HERO_2, HEALTH_HERO_3)),
				series.stream().map(oneSeries -> oneSeries.getLevels().stream().map(BadgeLevel::getBadge).toList()).toList());
	}

	@Test
	void reportsTheNumberOfProductsOfEveryBadgeFromTheRules() {
		givenAShopper();
		givenNoAverages();
		givenTheBadgeStanding(NOTHING_COUNTED, Set.of());

		var levels = levels(retrieve(LANGUAGE));

		assertEquals(Badge.values().length, levels.size());
		levels.forEach(level -> assertEquals(productsOf(level.getBadge()), level.getRequiredProducts(), level.getBadge().name()));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", " "})
	void labelsTheBadgesInEnglishWithoutALanguage(String language) {
		givenAShopper();
		givenNoAverages();
		givenTheBadgeStanding(NOTHING_COUNTED, Set.of());

		var labels = levels(retrieve(language)).stream().map(BadgeLevel::getLabel).toList();

		assertEquals("Environmental Hero Level 1", labels.getFirst());
		assertEquals("Health Hero Level 3", labels.getLast());
	}

	@Test
	void labelsTheBadgesInTheLanguageAsked() {
		givenAShopper();
		givenNoAverages();
		givenTheBadgeStanding(NOTHING_COUNTED, Set.of());

		var labels = levels(retrieve("xx")).stream().map(BadgeLevel::getLabel).toList();

		assertEquals("xx Environmental Hero Level 1", labels.getFirst());
		assertEquals("xx Health Hero Level 3", labels.getLast());
	}

	private Dashboard retrieve(String language) {
		return sut.retrieveDashboard(USER, MONTH, language).await().atMost(WAIT);
	}

	/**
	 * A user with Purchased Baskets, ahead of some of the other shoppers.
	 */
	private void givenAShopper() {
		givenTheUser(BASKET_COUNT, standing(10, 7));
	}

	/**
	 * The user's XP total and Streak, Purchased Baskets, and standing among the other shoppers.
	 */
	private void givenTheUser(long basketCount, XpStanding standing) {
		when(userProfileDao.requireXpAndStreak(any(), matchesHasUserId(USER)))
				.thenReturn(Uni.createFrom().item(ImmutableXpAndStreak.builder().xpTotal(XP_TOTAL).streak(STREAK).build()));
		when(userProfileDao.retrieveXpStanding(any(), matchesHasUserId(USER))).thenReturn(Uni.createFrom().item(standing));
		when(purchasedBasketDao.countBaskets(any(), matchesHasUserId(USER))).thenReturn(Uni.createFrom().item(basketCount));
	}

	private void givenNoAverages() {
		givenTheAverages(Optional.empty(), Optional.empty());
	}

	/**
	 * The averages of the month and of the month before it.
	 */
	private void givenTheAverages(Optional<Map<ScoredSustainabilityDimension, Double>> current, Optional<Map<ScoredSustainabilityDimension, Double>> previous) {
		when(purchasedBasketDao.retrieveAverageNormalisedScores(any(), matchesHasUserId(USER), eq(MONTH))).thenReturn(Uni.createFrom().item(current));
		when(purchasedBasketDao.retrieveAverageNormalisedScores(any(), matchesHasUserId(USER), eq(PREVIOUS_MONTH))).thenReturn(Uni.createFrom().item(previous));
	}

	/**
	 * The user's stored products above the threshold of the badges per dimension, and the badges they hold.
	 */
	private void givenTheBadgeStanding(Map<ScoredSustainabilityDimension, Long> storedCounts, Set<Badge> held) {
		when(purchasedBasketDao.retrieveProductCountsAbove(any(), matchesHasUserId(USER), eq(BADGE_RULES.normalisedScoreThreshold())))
				.thenReturn(Uni.createFrom().item(storedCounts));
		when(purchasedBasketDao.findBadges(any(), matchesHasUserId(USER))).thenReturn(Uni.createFrom().item(held));
	}

	private static XpStanding standing(long otherShoppers, long behind) {
		return ImmutableXpStanding.builder().otherShoppers(otherShoppers).behind(behind).build();
	}

	private static Optional<Map<ScoredSustainabilityDimension, Double>> averages(double environment, double animalWelfare, double socioEconomic, double health) {
		return Optional.of(Map.of(ENVIRONMENT, environment, ANIMAL_WELFARE, animalWelfare, SOCIO_ECONOMIC, socioEconomic, HEALTH, health));
	}

	private static Map<ScoredSustainabilityDimension, Long> counts(long environment, long animalWelfare, long socioEconomic, long health) {
		return Map.of(ENVIRONMENT, environment, ANIMAL_WELFARE, animalWelfare, SOCIO_ECONOMIC, socioEconomic, HEALTH, health);
	}

	private static DimensionProgress progress(ScoredSustainabilityDimension dimension, Double currentAverage, Double previousAverage, Double changePercent) {
		return ImmutableDimensionProgress.builder()
				.dimension(dimension)
				.currentAverage(currentAverage)
				.previousAverage(previousAverage)
				.changePercent(changePercent)
				.build();
	}

	private static long productsOf(Badge badge) {
		return BADGE_RULES.requirementOf(badge).products();
	}

	private static BadgeSeries seriesOf(Dashboard dashboard, ScoredSustainabilityDimension dimension) {
		return dashboard.getBadgeSeries().stream().filter(series -> series.getDimension() == dimension).findFirst().orElseThrow();
	}

	private static List<BadgeLevel> levels(Dashboard dashboard) {
		return dashboard.getBadgeSeries().stream().flatMap(series -> series.getLevels().stream()).toList();
	}

	private static List<Boolean> earned(BadgeSeries series) {
		return series.getLevels().stream().map(BadgeLevel::isEarned).toList();
	}
}
