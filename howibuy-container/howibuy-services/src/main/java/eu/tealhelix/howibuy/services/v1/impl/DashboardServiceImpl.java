package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forcm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.dao.PurchasedBasketDao;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import eu.tealhelix.howibuy.services.model.XpStanding;
import eu.tealhelix.howibuy.services.v1.DashboardService;
import eu.tealhelix.howibuy.services.v1.basket.BadgeRules;
import eu.tealhelix.howibuy.services.v1.basket.BasketRules;
import eu.tealhelix.howibuy.services.v1.basket.Labels;
import eu.tealhelix.howibuy.v1.model.BadgeLevel;
import eu.tealhelix.howibuy.v1.model.BadgeSeries;
import eu.tealhelix.howibuy.v1.model.Dashboard;
import eu.tealhelix.howibuy.v1.model.DimensionProgress;
import eu.tealhelix.howibuy.v1.model.ImmutableBadgeLevel;
import eu.tealhelix.howibuy.v1.model.ImmutableBadgeSeries;
import eu.tealhelix.howibuy.v1.model.ImmutableDashboard;
import eu.tealhelix.howibuy.v1.model.ImmutableDimensionProgress;
import eu.tealhelix.howibuy.v1.types.Badge;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class DashboardServiceImpl implements DashboardService {
	/**
	 * The language of the labels when none is asked for.
	 */
	private static final String ENGLISH = "en";
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final TealHelixAuthorization authorization;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final UserProfileDao userProfileDao;
	private final PurchasedBasketDao purchasedBasketDao;
	private final BasketRules basketRules = new BasketRules();
	private final BadgeRules badgeRules = new BadgeRules();

	@Inject
	public DashboardServiceImpl(
			TealHelixAuthorization authorization,
			ReactivePersistenceContextFactory persistenceContextFactory,
			UserProfileDao userProfileDao,
			PurchasedBasketDao purchasedBasketDao
	) {
		this.authorization = authorization;
		this.persistenceContextFactory = persistenceContextFactory;
		this.userProfileDao = userProfileDao;
		this.purchasedBasketDao = purchasedBasketDao;
	}

	@Override
	public Uni<Dashboard> retrieveDashboard(User user, YearMonth month, String language) {
		authorization.requirePerson(user);
		if (month == null) {
			throw RequiredInputMissingException.fromRequiredInputName("month");
		}
		var labelLanguage = language == null || language.isBlank() ? ENGLISH : language;
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				userProfileDao.requireXpAndStreak(em, user),
				_ -> userProfileDao.retrieveXpStanding(em, user),
				(_, _) -> purchasedBasketDao.countBaskets(em, user),
				(_, _, _) -> purchasedBasketDao.retrieveAverageNormalisedScores(em, user, month),
				(_, _, _, _) -> purchasedBasketDao.retrieveAverageNormalisedScores(em, user, month.minusMonths(1)),
				(_, _, _, _, _) -> purchasedBasketDao.retrieveProductCountsAbove(em, user, badgeRules.normalisedScoreThreshold()),
				(_, _, _, _, _, _) -> purchasedBasketDao.findBadges(em, user),
				(xpAndStreak, standing, basketCount, currentAverages, previousAverages, productCounts, held) -> ImmutableDashboard.builder()
						.xpTotal(xpAndStreak.getXpTotal())
						.xpPercentile(percentile(basketCount, standing))
						.xpPerProduct(basketRules.xpPerProduct())
						.streak(xpAndStreak.getStreak())
						.purchasedBasketCount(basketCount)
						.monthlyProgress(monthlyProgress(currentAverages, previousAverages))
						.badgeSeries(badgeSeries(productCounts, held, labelLanguage))
						.build()));
	}

	/**
	 * Of the other shoppers, the percentage behind the user, rounded down; {@code null} for a user without a Purchased
	 * Basket, who is not among the shoppers, and for a user without other shoppers.
	 */
	private static Integer percentile(long basketCount, XpStanding standing) {
		if (basketCount == 0 || standing.getOtherShoppers() == 0) {
			return null;
		}
		return (int) (100 * standing.getBehind() / standing.getOtherShoppers());
	}

	private static List<DimensionProgress> monthlyProgress(
			Optional<Map<ScoredSustainabilityDimension, Double>> currentAverages, Optional<Map<ScoredSustainabilityDimension, Double>> previousAverages) {
		return Arrays.stream(ScoredSustainabilityDimension.values())
				.map(dimension -> progress(dimension, roundedAverage(currentAverages, dimension), roundedAverage(previousAverages, dimension)))
				.toList();
	}

	/**
	 * The average of a dimension, rounded to two decimals, half up. The rounding starts from the shortest decimal that
	 * reads as the average, so 0.845 is 0.85, though the {@code double} nearest to 0.845 is a little below it.
	 */
	private static Optional<BigDecimal> roundedAverage(Optional<Map<ScoredSustainabilityDimension, Double>> averages, ScoredSustainabilityDimension dimension) {
		return averages.map(byDimension -> BigDecimal.valueOf(byDimension.get(dimension)).setScale(2, RoundingMode.HALF_UP));
	}

	private static DimensionProgress progress(ScoredSustainabilityDimension dimension, Optional<BigDecimal> current, Optional<BigDecimal> previous) {
		return ImmutableDimensionProgress.builder()
				.dimension(dimension)
				.currentAverage(current.map(BigDecimal::doubleValue).orElse(null))
				.previousAverage(previous.map(BigDecimal::doubleValue).orElse(null))
				.changePercent(changePercent(current, previous).map(BigDecimal::doubleValue).orElse(null))
				.build();
	}

	/**
	 * The change from the previous rounded average to the current one, in percent of the previous one, rounded to one
	 * decimal, half up; none without either average, nor from a previous one of 0.00.
	 */
	private static Optional<BigDecimal> changePercent(Optional<BigDecimal> current, Optional<BigDecimal> previous) {
		return previous
				.filter(from -> from.signum() != 0)
				.flatMap(from -> current.map(to -> to.subtract(from).multiply(HUNDRED).divide(from, 1, RoundingMode.HALF_UP)));
	}

	private List<BadgeSeries> badgeSeries(Map<ScoredSustainabilityDimension, Long> productCounts, Set<Badge> held, String language) {
		return Arrays.stream(ScoredSustainabilityDimension.values())
				.map(dimension -> series(dimension, productCounts.get(dimension), held, language))
				.toList();
	}

	private BadgeSeries series(ScoredSustainabilityDimension dimension, long productCount, Set<Badge> held, String language) {
		return ImmutableBadgeSeries.builder()
				.dimension(dimension)
				.productCount(productCount)
				.levels(Arrays.stream(Badge.values())
						.filter(badge -> badgeRules.requirementOf(badge).dimension() == dimension)
						.map(badge -> level(badge, held, language))
						.toList())
				.build();
	}

	/**
	 * A level of a series, earned when the user holds its badge, whatever the count: a badge is kept once earned, and is
	 * earned only when a basket awards it.
	 */
	private BadgeLevel level(Badge badge, Set<Badge> held, String language) {
		return ImmutableBadgeLevel.builder()
				.badge(badge)
				.label(Labels.BADGES.labelOf(badge, language))
				.requiredProducts(badgeRules.requirementOf(badge).products())
				.isEarned(held.contains(badge))
				.build();
	}
}
