package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;
import static eu.tealhelix.common.utils.UniComprehensions.forcm;
import static eu.tealhelix.howibuy.v1.types.BasketAssessmentOutcomeType.ASSESSED;
import static eu.tealhelix.howibuy.v1.types.BasketAssessmentOutcomeType.NOTHING_TO_ASSESS;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.HasUserId;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.howibuy.dao.CatalogueDao;
import eu.tealhelix.howibuy.dao.PurchasedBasketDao;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.scoring.v1.ScoredProduct;
import eu.tealhelix.howibuy.services.model.ImmutablePurchasedBasket;
import eu.tealhelix.howibuy.services.model.ImmutablePurchasedBasketProduct;
import eu.tealhelix.howibuy.services.model.PurchasedBasketProduct;
import eu.tealhelix.howibuy.services.model.XpAndStreak;
import eu.tealhelix.howibuy.services.v1.BasketAssessmentService;
import eu.tealhelix.howibuy.services.v1.basket.BadgeRules;
import eu.tealhelix.howibuy.services.v1.basket.BasketRules;
import eu.tealhelix.howibuy.services.v1.basket.Labels;
import eu.tealhelix.howibuy.services.v1.basket.types.BasketVerdict;
import eu.tealhelix.howibuy.services.v1.basket.types.ScoredBasketProduct;
import eu.tealhelix.howibuy.v1.model.BasketAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.EarnedBadge;
import eu.tealhelix.howibuy.v1.model.ImmutableBasketAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ImmutableEarnedBadge;
import eu.tealhelix.howibuy.v1.types.Badge;
import eu.tealhelix.howibuy.v1.types.BasketAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.BasketMatch;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.StreakEffect;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class BasketAssessmentServiceImpl implements BasketAssessmentService {
	private static final Logger LOG = LoggerFactory.getLogger(BasketAssessmentServiceImpl.class);
	/**
	 * How far ahead of the clock of this application a purchase time may be, for a Retailer whose clock runs fast.
	 */
	static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

	private final TealHelixAuthorization authorization;
	private final ReferenceProductIdentifier referenceProductIdentifier;
	private final ArchetypeCorpus archetypeCorpus;
	private final PersonalWeightsProvider personalWeightsProvider;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final UserProfileDao userProfileDao;
	private final CatalogueDao catalogueDao;
	private final PurchasedBasketDao purchasedBasketDao;
	private final DateTimeService dateTimeService;
	private final BasketRules basketRules = new BasketRules();
	private final BadgeRules badgeRules = new BadgeRules();
	private final Labels<BasketMatch> basketMatchLabels = new Labels<>(Labels.class.getPackageName() + ".BasketMatchLabels");
	private final Labels<Badge> badgeLabels = new Labels<>(Labels.class.getPackageName() + ".BadgeLabels");

	/**
	 * A product of the basket that has an Overall Score.
	 *
	 * @param productKey             the retailer's key of the product
	 * @param scoredProduct          the archetype the product was identified as, with its normalised scores
	 * @param personalOverallScore   the Overall Score of the archetype under the Weighting Profile in force for the user
	 * @param scientificOverallScore the Overall Score of the archetype under the scientific Weighting Profile
	 */
	private record ScoredBasketLine(ProductKey productKey, ScoredProduct scoredProduct, double personalOverallScore, double scientificOverallScore) {
		ScoredBasketProduct toBasketProduct() {
			return new ScoredBasketProduct(productKey, scientificOverallScore, personalOverallScore, scoredProduct.normalisedScores());
		}

		PurchasedBasketProduct toPurchasedBasketProduct() {
			return ImmutablePurchasedBasketProduct.builder()
					.productKey(productKey)
					.archetypeProductId(scoredProduct.productId())
					.personalOverallScore(personalOverallScore)
					.scientificOverallScore(scientificOverallScore)
					.normalisedScores(scoredProduct.normalisedScores())
					.build();
		}
	}

	/**
	 * The scored products of a basket and the compass Attempt the Weighting Profile in force was derived from.
	 *
	 * @param lines            the products of the basket that have an Overall Score
	 * @param compassAttemptId the compass Attempt, {@code null} when the scientific profile was in force
	 */
	private record ScoredBasket(List<ScoredBasketLine> lines, @Nullable UUID compassAttemptId) {
		BasketVerdict judge(BasketRules basketRules) {
			return basketRules.judge(basketProducts());
		}

		List<Badge> earnedBadges(BadgeRules badgeRules, Map<ScoredSustainabilityDimension, Long> storedCounts, Set<Badge> held) {
			return badgeRules.earnedBadges(storedCounts, held, basketProducts());
		}

		private List<ScoredBasketProduct> basketProducts() {
			return lines.stream().map(ScoredBasketLine::toBasketProduct).toList();
		}
	}

	@Inject
	public BasketAssessmentServiceImpl(
			TealHelixAuthorization authorization,
			ReferenceProductIdentifier referenceProductIdentifier,
			ArchetypeCorpus archetypeCorpus,
			PersonalWeightsProvider personalWeightsProvider,
			ReactivePersistenceContextFactory persistenceContextFactory,
			UserProfileDao userProfileDao,
			CatalogueDao catalogueDao,
			PurchasedBasketDao purchasedBasketDao,
			DateTimeService dateTimeService
	) {
		this.authorization = authorization;
		this.referenceProductIdentifier = referenceProductIdentifier;
		this.archetypeCorpus = archetypeCorpus;
		this.personalWeightsProvider = personalWeightsProvider;
		this.persistenceContextFactory = persistenceContextFactory;
		this.userProfileDao = userProfileDao;
		this.catalogueDao = catalogueDao;
		this.purchasedBasketDao = purchasedBasketDao;
		this.dateTimeService = dateTimeService;
	}

	@Override
	public Uni<BasketAssessmentOutcome> assessBasketPrePurchase(User user, CatalogueId catalogueId, List<ProductKey> productKeys, String language) {
		authorization.requirePerson(user);
		LOG.info("Assess basket before purchase, user: {}, catalogue: {}, language: {}, keys: {}", user.getId().asString(), catalogueId.asString(), language,
				productKeys.stream().map(ProductKey::asString).toList());
		return forc(
				scoreBasket(user, catalogueId, productKeys),
				scoredBasket -> persistenceContextFactory.withoutTransaction(em -> forcm(
						earnedBadges(em, user, scoredBasket),
						_ -> userProfileDao.requireXpAndStreak(em, user),
						(earnedBadges, xpAndStreak) -> outcome(scoredBasket.judge(basketRules), earnedBadges, language, xpAndStreak))));
	}

	@Override
	public Uni<BasketAssessmentOutcome> assessPurchasedBasket(
			User user, CatalogueId catalogueId, OffsetDateTime purchasedAt, List<ProductKey> productKeys, String language) {
		authorization.requirePerson(user);
		var recordedAt = dateTimeService.getNow();
		requireNotInTheFuture(purchasedAt, recordedAt);
		LOG.info("Assess purchased basket, user: {}, catalogue: {}, purchasedAt: {}, language: {}, keys: {}", user.getId().asString(), catalogueId.asString(),
				purchasedAt, language, productKeys.stream().map(ProductKey::asString).toList());
		return persistenceContextFactory.withTransaction(tx -> forcm(
				userProfileDao.lock(tx, user),
				_ -> scoreBasket(user, catalogueId, productKeys),
				(_, _) -> requireRetailerOf(tx, catalogueId),
				(_, scoredBasket, _) -> earnedBadges(tx, user, scoredBasket),
				(_, scoredBasket, retailerId, earnedBadges) -> {
					BasketVerdict verdict = scoredBasket.judge(basketRules);
					var purchasedBasket = ImmutablePurchasedBasket.builder()
									.userId(user.getId())
									.retailerId(retailerId)
									.catalogueId(catalogueId)
									.purchasedAt(purchasedAt)
									.recordedAt(recordedAt)
									.type(typeOf(verdict))
									.basketMatch(verdict.basketMatch())
									.xpValue(verdict.xpValue())
									.compassAttemptId(scoredBasket.compassAttemptId())
									.products(scoredBasket.lines().stream().map(ScoredBasketLine::toPurchasedBasketProduct).toList())
									.earnedBadges(earnedBadges)
									.build();
					return purchasedBasketDao.store(tx, purchasedBasket).replaceWith(verdict);
				},
				(_, _, _, _, verdict) -> userProfileDao.addXp(tx, user, verdict.xpValue()),
				(_, _, _, _, verdict, _) -> applyStreakEffect(tx, user, verdict.streakEffect()),
				(_, _, _, _, _, _, _) -> userProfileDao.requireXpAndStreak(tx, user),
				(_, _, _, earnedBadges, verdict, _, _, xpAndStreak) -> outcome(verdict, earnedBadges, language, xpAndStreak)
		));
	}

	/**
	 * Refuse a purchase time ahead of the clock of this application by more than {@link #MAX_CLOCK_SKEW}. The instants
	 * are compared, so a shop whose local time runs ahead of the server's is not in the future.
	 *
	 * @param recordedAt The time of recording, in the server's time zone
	 */
	private static void requireNotInTheFuture(OffsetDateTime purchasedAt, LocalDateTime recordedAt) {
		var latest = recordedAt.atZone(ZoneId.systemDefault()).toInstant().plus(MAX_CLOCK_SKEW);
		if (purchasedAt.toInstant().isAfter(latest)) {
			throw BadInputValueException.fromInputNameAndHint("purchasedAt", "The purchase time is in the future, purchasedAt: " + purchasedAt);
		}
	}

	private Uni<RetailerId> requireRetailerOf(ReactivePersistenceContext em, CatalogueId catalogueId) {
		return catalogueDao.findRetailers(em, Set.of(catalogueId))
				.map(retailers -> Optional.ofNullable(retailers.get(catalogueId)).orElseThrow(() -> new NotFoundException(catalogueId)));
	}

	/**
	 * The badges the basket earns, from what the user has towards them as stored. Both the assessment before the purchase
	 * and the purchase compute them so, before anything of the basket is stored.
	 */
	private Uni<List<Badge>> earnedBadges(ReactivePersistenceContext em, HasUserId userId, ScoredBasket scoredBasket) {
		return forcm(
				purchasedBasketDao.retrieveProductCountsAbove(em, userId, badgeRules.normalisedScoreThreshold()),
				_ -> purchasedBasketDao.findBadges(em, userId),
				(storedCounts, held) -> scoredBasket.earnedBadges(badgeRules, storedCounts, held));
	}

	/**
	 * Apply a purchased basket's effect to the user's Streak: {@code EXTEND} increases it by one, {@code RESET} sets it
	 * to 0, {@code NONE} leaves it as it is.
	 */
	private Uni<Void> applyStreakEffect(ReactivePersistenceTxContext tx, HasUserId userId, StreakEffect streakEffect) {
		return switch (streakEffect) {
			case EXTEND -> userProfileDao.increaseStreak(tx, userId);
			case RESET -> userProfileDao.resetStreak(tx, userId);
			case NONE -> Uni.createFrom().voidItem();
		};
	}

	/**
	 * The products of the basket that have an Overall Score under the user's Weighting Profile, each once, in the order
	 * first sent. A product that is unknown, not classified, matches no archetype, or whose archetype has no Overall
	 * Score (it is outside the Nutri-Score scheme) is left out. The identifications and the corpus are read once for the
	 * whole basket.
	 */
	private Uni<ScoredBasket> scoreBasket(User user, CatalogueId catalogueId, List<ProductKey> productKeys) {
		var distinctKeys = new LinkedHashSet<>(productKeys);
		return forcm(
				personalWeightsProvider.forUser(user),
				_ -> referenceProductIdentifier.identify(catalogueId, distinctKeys),
				(_, _) -> archetypeCorpus.scoredArchetypes(),
				(weights, identifications, archetypes) ->
						new ScoredBasket(scoredLines(distinctKeys, identifications, archetypes, weights.profile()), weights.compassAttemptId()));
	}

	private static List<ScoredBasketLine> scoredLines(
			Set<ProductKey> productKeys, Map<ProductKey, ProductIdentification> identifications, ScoredArchetypes archetypes, WeightProfile profile) {
		return productKeys.stream()
				.flatMap(productKey -> scoredLine(productKey, identifications.get(productKey), archetypes, profile).stream())
				.toList();
	}

	private static Optional<ScoredBasketLine> scoredLine(
			ProductKey productKey, ProductIdentification identification, ScoredArchetypes archetypes, WeightProfile profile) {
		return switch (identification) {
			case ProductIdentification.Identified identified -> archetypes.find(identified.archetypeProductId())
					.map(scoredProduct -> new ScoredBasketLine(
							productKey, scoredProduct, scoredProduct.overallScore(profile), scoredProduct.overallScore(ScientificWeights.profile())));
			case ProductIdentification.NotIdentified _ -> Optional.empty();
		};
	}

	private BasketAssessmentOutcome outcome(BasketVerdict verdict, List<Badge> earnedBadges, String language, XpAndStreak xpAndStreak) {
		var basketMatch = verdict.basketMatch();
		return ImmutableBasketAssessmentOutcome.builder()
				.type(typeOf(verdict))
				.basketMatch(basketMatch)
				.basketMatchLabel(basketMatch == null ? null : basketMatchLabels.labelOf(basketMatch, language))
				.problematicProductKeys(verdict.problematicProductKeys())
				.xpValue(verdict.xpValue())
				.totalXp(xpAndStreak.getXpTotal())
				.streakEffect(verdict.streakEffect())
				.streak(xpAndStreak.getStreak())
				.earnedBadges(earnedBadges.stream().map(badge -> earnedBadge(badge, language)).toList())
				.build();
	}

	private EarnedBadge earnedBadge(Badge badge, String language) {
		return ImmutableEarnedBadge.builder().badge(badge).label(badgeLabels.labelOf(badge, language)).build();
	}

	private static BasketAssessmentOutcomeType typeOf(BasketVerdict verdict) {
		return verdict.basketMatch() == null ? NOTHING_TO_ASSESS : ASSESSED;
	}
}
