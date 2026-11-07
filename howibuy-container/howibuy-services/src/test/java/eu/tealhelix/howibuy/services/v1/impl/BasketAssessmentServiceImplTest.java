package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.v1.types.UserIdTestUtils.matchesHasUserId;
import static eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.impacts;
import static eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.personalProfileOnEnvironment;
import static eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.productId;
import static eu.tealhelix.howibuy.v1.types.BasketAssessmentOutcomeType.ASSESSED;
import static eu.tealhelix.howibuy.v1.types.BasketAssessmentOutcomeType.NOTHING_TO_ASSESS;
import static eu.tealhelix.howibuy.v1.types.IdentificationFailureReason.NOT_CLASSIFIED;
import static eu.tealhelix.howibuy.v1.types.IdentificationFailureReason.NO_MATCHING_ARCHETYPE;
import static eu.tealhelix.howibuy.v1.types.IdentificationFailureReason.UNKNOWN_PRODUCT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.CatalogueDao;
import eu.tealhelix.howibuy.dao.PurchasedBasketDao;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.services.model.ImmutablePurchasedBasket;
import eu.tealhelix.howibuy.services.model.ImmutablePurchasedBasketProduct;
import eu.tealhelix.howibuy.services.model.ImmutableXpAndStreak;
import eu.tealhelix.howibuy.services.model.PurchasedBasket;
import eu.tealhelix.howibuy.services.model.PurchasedBasketProduct;
import eu.tealhelix.howibuy.services.model.XpAndStreak;
import eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.Branch;
import eu.tealhelix.howibuy.v1.model.BasketAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.EarnedBadge;
import eu.tealhelix.howibuy.v1.model.ImmutableEarnedBadge;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.Badge;
import eu.tealhelix.howibuy.v1.types.BasketMatch;
import eu.tealhelix.howibuy.v1.types.IdentificationFailureReason;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.StreakEffect;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The basket is judged by the real {@code BasketRules} over a real scored corpus, built with
 * {@link ArchetypeImpactsFixtures} so that each Overall Score is known exactly. Every dimension holds two impacts, each
 * at two products, so every normalised score is exactly 1 or 0; for animal welfare and socio-economic too, since the
 * impacts 0 and 1, log-transformed around their median 1, become log 1 and log 2, the two ends of the scale:
 *
 * <pre>
 *            E     AW    S     H     scientific  environment only
 * Good       1.0   1.0   1.0   1.0   1.0         1.0
 * Fair       0.0   1.0   1.0   1.0   0.75        0.0
 * Poor       0.0   0.0   0.0   0.0   0.0         0.0
 * Green      1.0   0.0   0.0   0.0   0.25        1.0
 * Spirits    —     —     —     —     none        none
 * </pre>
 *
 * Only Good is above 0.8 on the scientific Overall Score.
 */
@ExtendWith(MockitoExtension.class)
class BasketAssessmentServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final User USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, true, Set.of());
	private static final User SERVICE_USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, false, Set.of());
	private static final CatalogueId CATALOGUE = new CatalogueIdImpl("test-gr");
	private static final String LANGUAGE = "en";
	private static final int TOTAL_XP = 17;
	private static final int STREAK = 4;
	private static final UUID ATTEMPT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final RetailerId RETAILER = new GenericRetailerId("8d1e5b4a-7c2f-4e93-a6b0-3f9d2c1e7a54");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0);
	/**
	 * Two hours before the server's clock, at the server's offset.
	 */
	private static final OffsetDateTime EARLIER = NOW.minusHours(2).atZone(ZoneId.systemDefault()).toOffsetDateTime();
	private static final Map<ScoredSustainabilityDimension, Long> NOTHING_COUNTED = counts(0, 0, 0, 0);

	private static final Branch DAIRY = new Branch("Dairy", "Milk", "Cow milk");
	private static final ArchetypeProductId GOOD_ARCHETYPE = productId("good");
	private static final ArchetypeProductId FAIR_ARCHETYPE = productId("fair");
	private static final ArchetypeProductId POOR_ARCHETYPE = productId("poor");
	private static final ArchetypeProductId GREEN_ARCHETYPE = productId("green");
	private static final ArchetypeProductId SPIRITS_ARCHETYPE = productId("spirits");
	private static final ScoredArchetypes ARCHETYPES = ScoredArchetypes.of(
			List.of(
					impacts(GOOD_ARCHETYPE, "Good", "100", DAIRY, 0.0, 0.0, 0.0, "Nutriscore_A", 0),
					impacts(FAIR_ARCHETYPE, "Fair", "200", DAIRY, 1.0, 0.0, 0.0, "Nutriscore_A", 0),
					impacts(POOR_ARCHETYPE, "Poor", "300", DAIRY, 1.0, 1.0, 1.0, "Nutriscore_E", 20),
					impacts(GREEN_ARCHETYPE, "Green", "400", DAIRY, 0.0, 1.0, 1.0, "Nutriscore_E", 20),
					impacts(SPIRITS_ARCHETYPE, "Spirits", "500", DAIRY, 0.5, 0.5, 0.5, "0", 0)),
			List.of());

	private static final ProductKey GOOD = new ProductKeyImpl("good-key");
	private static final ProductKey FAIR = new ProductKeyImpl("fair-key");
	private static final ProductKey POOR = new ProductKeyImpl("poor-key");
	private static final ProductKey GREEN = new ProductKeyImpl("green-key");
	private static final ProductKey SPIRITS = new ProductKeyImpl("spirits-key");
	private static final ProductKey UNKNOWN = new ProductKeyImpl("unknown-key");
	private static final ProductKey UNCLASSIFIED = new ProductKeyImpl("unclassified-key");
	private static final ProductKey NO_ARCHETYPE = new ProductKeyImpl("no-archetype-key");

	private static final Map<ProductKey, ProductIdentification> IDENTIFICATIONS = Map.of(
			GOOD, identified(GOOD_ARCHETYPE),
			FAIR, identified(FAIR_ARCHETYPE),
			POOR, identified(POOR_ARCHETYPE),
			GREEN, identified(GREEN_ARCHETYPE),
			SPIRITS, identified(SPIRITS_ARCHETYPE),
			UNKNOWN, notIdentified(UNKNOWN_PRODUCT),
			UNCLASSIFIED, notIdentified(NOT_CLASSIFIED),
			NO_ARCHETYPE, notIdentified(NO_MATCHING_ARCHETYPE));

	@Mock
	ReferenceProductIdentifier referenceProductIdentifier;

	@Mock
	ArchetypeCorpus archetypeCorpus;

	@Mock
	PersonalWeightsProvider personalWeightsProvider;

	@Mock
	UserProfileDao userProfileDao;

	@Mock
	CatalogueDao catalogueDao;

	@Mock
	PurchasedBasketDao purchasedBasketDao;

	@Mock
	DateTimeService dateTimeService;

	@Captor
	ArgumentCaptor<PurchasedBasket> storedBasket;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	private BasketAssessmentServiceImpl sut;

	@BeforeEach
	void setUp() {
		sut = new BasketAssessmentServiceImpl(
				new TealHelixAuthorizationImpl(), referenceProductIdentifier, archetypeCorpus, personalWeightsProvider,
				persistenceContextFactory, userProfileDao, catalogueDao, purchasedBasketDao, dateTimeService);
	}

	@Test
	void rejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.assessBasketPrePurchase(SERVICE_USER, CATALOGUE, List.of(GOOD), LANGUAGE));
	}

	/**
	 * {@link AssessmentInputsTest} tests the checks themselves.
	 */
	@Test
	void checksTheLanguageAndTheProductKeys() {
		var e = assertThrows(RequiredInputMissingException.class, () -> sut.assessBasketPrePurchase(USER, CATALOGUE, List.of(GOOD), null));

		assertEquals("language", e.getInputName());
		verifyNoInteractions(referenceProductIdentifier, userProfileDao);
	}

	@Test
	void judgesTheBasketByTheOverallScoresOfItsProducts() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD, FAIR, POOR));
		givenTheUsersStanding();

		var outcome = assess(List.of(FAIR, GOOD, POOR));

		assertEquals(ASSESSED, outcome.getType());
		assertEquals(BasketMatch.NEEDS_IMPROVEMENT, outcome.getBasketMatch(), "an average of 0.58");
		assertEquals("Needs improvement", outcome.getBasketMatchLabel());
		assertEquals(10, outcome.getXpValue(), "the good product alone is above 0.8, and three products are too few for a bonus");
		assertEquals(List.of(POOR, FAIR), outcome.getProblematicProductKeys(), "at 0.8 or below, lowest Overall Score first");
		assertEquals(TOTAL_XP, outcome.getTotalXp(), "the user's total as it stands: nothing is stored before the purchase");
		assertEquals(StreakEffect.EXTEND, outcome.getStreakEffect(), "the good product is above 0.8");
		assertEquals(STREAK, outcome.getStreak(), "the user's Streak as it stands");
		assertNull(outcome.getIntervention());
	}

	@Test
	void leavesOutTheProductsThatHaveNoOverallScore() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD, UNKNOWN, UNCLASSIFIED, NO_ARCHETYPE, SPIRITS));
		givenTheUsersStanding();

		var outcome = assess(List.of(GOOD, UNKNOWN, UNCLASSIFIED, NO_ARCHETYPE, SPIRITS));

		assertEquals(ASSESSED, outcome.getType());
		assertEquals(BasketMatch.EXCELLENT, outcome.getBasketMatch(), "only the good product counts");
		assertEquals(10, outcome.getXpValue());
		assertEquals(List.of(), outcome.getProblematicProductKeys(), "a product that cannot be judged does not count against the user");
	}

	@Test
	void countsARepeatedProductOnce() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD, FAIR));
		givenTheUsersStanding();

		var outcome = assess(List.of(GOOD, FAIR, GOOD, FAIR, GOOD));

		assertEquals(10, outcome.getXpValue(), "one product above 0.8, and two products are too few for a bonus");
		assertEquals(List.of(FAIR), outcome.getProblematicProductKeys());
	}

	@Test
	void matchesTheBasketUnderTheUsersPersonalWeightsAndJudgesTheRestUnderTheScientificOnes() {
		givenWeights(personalProfileOnEnvironment());
		givenIdentifications(Set.of(GREEN));
		givenTheUsersStanding();

		var outcome = assess(List.of(GREEN));

		assertEquals(BasketMatch.EXCELLENT, outcome.getBasketMatch(), "1.0 for a user who cares only about the environment");
		assertEquals(0, outcome.getXpValue(), "0.25 under the scientific weights");
		assertEquals(List.of(GREEN), outcome.getProblematicProductKeys());
		assertEquals(StreakEffect.RESET, outcome.getStreakEffect());
	}

	@Test
	void hasNothingToAssessWhenNoProductHasAnOverallScore() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(UNKNOWN, SPIRITS));
		givenTheUsersStanding();

		assertNothingToAssess(assess(List.of(UNKNOWN, SPIRITS)));
	}

	@Test
	void hasNothingToAssessInAnEmptyBasket() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of());
		givenTheUsersStanding();

		assertNothingToAssess(assess(List.of()));
	}

	/**
	 * The good and the green products are above 0.8 on the environment, the good one alone on the other dimensions.
	 */
	@Test
	void reportsTheBadgesTheBasketWouldEarnWithoutStoringThem() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD, GREEN));
		givenTheUsersStanding(counts(48, 49, 49, 0), Set.of(Badge.ANIMAL_WELFARE_HERO_1));

		var outcome = assess(List.of(GOOD, GREEN));

		assertEquals(List.of(earned(Badge.ENVIRONMENTAL_HERO_1, "Environmental Hero Level 1"), earned(Badge.SOCIO_ECONOMIC_HERO_1, "Socio-economic Hero Level 1")),
				outcome.getEarnedBadges(), "not the badge of animal welfare, which the user holds");
		verify(purchasedBasketDao, never()).store(any(), any());
		verify(userProfileDao, never()).lock(any(), any());
	}

	@Test
	void rejectsAServiceUserRegisteringAPurchase() {
		assertThrows(NotAuthorizedException.class, () -> sut.assessPurchasedBasket(SERVICE_USER, CATALOGUE, EARLIER, List.of(GOOD), LANGUAGE));
	}

	@Test
	void storesThePurchasedBasketWithItsScoredProducts() {
		var personal = personalProfileOnEnvironment();
		givenWeights(personal);
		givenIdentifications(Set.of(GOOD, FAIR, POOR, UNKNOWN));
		givenThePurchaseIsStored(10, StreakEffect.EXTEND, STREAK + 1);

		var outcome = purchase(EARLIER, List.of(FAIR, GOOD, POOR, UNKNOWN));

		assertEquals(ASSESSED, outcome.getType());
		assertEquals(BasketMatch.NEEDS_IMPROVEMENT, outcome.getBasketMatch());
		assertEquals(10, outcome.getXpValue());
		assertEquals(List.of(POOR, FAIR), outcome.getProblematicProductKeys());
		assertEquals(
				ImmutablePurchasedBasket.builder()
						.userId(USER.getId())
						.retailerId(RETAILER)
						.catalogueId(CATALOGUE)
						.purchasedAt(EARLIER)
						.recordedAt(NOW)
						.type(ASSESSED)
						.basketMatch(BasketMatch.NEEDS_IMPROVEMENT)
						.xpValue(10)
						.compassAttemptId(ATTEMPT_ID)
						.products(List.of(
								product(FAIR, FAIR_ARCHETYPE, personal),
								product(GOOD, GOOD_ARCHETYPE, personal),
								product(POOR, POOR_ARCHETYPE, personal)))
						.build(),
				storedBasket.getValue(),
				"the scored products in the order sent, the unknown one left out");
	}

	@Test
	void addsTheXpAndIncreasesTheStreakBeforeReadingThem() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD, FAIR));
		givenThePurchaseIsStored(10, StreakEffect.EXTEND, STREAK + 1);

		var outcome = purchase(EARLIER, List.of(GOOD, FAIR));

		assertEquals(TOTAL_XP + 10, outcome.getTotalXp(), "the total includes this basket");
		assertEquals(StreakEffect.EXTEND, outcome.getStreakEffect());
		assertEquals(STREAK + 1, outcome.getStreak(), "the Streak includes this basket");
		var order = inOrder(purchasedBasketDao, userProfileDao);
		order.verify(purchasedBasketDao).store(any(), any());
		order.verify(userProfileDao).addXp(any(), matchesHasUserId(USER), eq(10));
		order.verify(userProfileDao).increaseStreak(any(), matchesHasUserId(USER));
		order.verify(userProfileDao).requireXpAndStreak(any(), matchesHasUserId(USER));
	}

	@Test
	void storesTheBadgesThePurchaseEarnsWithTheBasket() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD));
		givenThePurchaseIsStored(10, StreakEffect.EXTEND, STREAK + 1, counts(49, 0, 0, 199), Set.of(Badge.HEALTH_HERO_1, Badge.HEALTH_HERO_2));

		var outcome = purchase(EARLIER, List.of(GOOD));

		assertEquals(List.of(earned(Badge.ENVIRONMENTAL_HERO_1, "Environmental Hero Level 1"), earned(Badge.HEALTH_HERO_3, "Health Hero Level 3")), outcome.getEarnedBadges());
		assertEquals(List.of(Badge.ENVIRONMENTAL_HERO_1, Badge.HEALTH_HERO_3), storedBasket.getValue().getEarnedBadges());
	}

	/**
	 * Earning a badge reads what the user has towards it and then stores the basket, so another purchase of the same
	 * user must not come in between: the profile is locked first.
	 */
	@Test
	void locksTheUsersProfileBeforeReadingWhatTheyHaveTowardsTheBadges() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD));
		givenThePurchaseIsStored(10, StreakEffect.EXTEND, STREAK + 1);

		purchase(EARLIER, List.of(GOOD));

		var order = inOrder(userProfileDao, purchasedBasketDao);
		order.verify(userProfileDao).lock(any(), matchesHasUserId(USER));
		order.verify(purchasedBasketDao).retrieveProductCountsAbove(any(), matchesHasUserId(USER), eq(0.8));
		order.verify(purchasedBasketDao).findBadges(any(), matchesHasUserId(USER));
		order.verify(purchasedBasketDao).store(any(), any());
	}

	@Test
	void resetsTheStreakWithAPurchaseWithoutAProductAbove08() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(FAIR, POOR));
		givenThePurchaseIsStored(0, StreakEffect.RESET, 0);

		var outcome = purchase(EARLIER, List.of(FAIR, POOR));

		assertEquals(StreakEffect.RESET, outcome.getStreakEffect());
		assertEquals(0, outcome.getStreak());
		verify(userProfileDao).resetStreak(any(), matchesHasUserId(USER));
	}

	@Test
	void storesAPurchaseWithNothingToAssessWithoutProducts() {
		givenScientificWeightsWithoutAnAttempt();
		givenIdentifications(Set.of());
		givenThePurchaseIsStored(0, StreakEffect.NONE, STREAK);

		var outcome = purchase(EARLIER, List.of());

		assertEquals(NOTHING_TO_ASSESS, outcome.getType());
		assertEquals(TOTAL_XP, outcome.getTotalXp());
		assertEquals(StreakEffect.NONE, outcome.getStreakEffect());
		assertEquals(STREAK, outcome.getStreak(), "the Streak as it stood");
		verify(userProfileDao, never()).increaseStreak(any(), any());
		verify(userProfileDao, never()).resetStreak(any(), any());
		var basket = storedBasket.getValue();
		assertEquals(NOTHING_TO_ASSESS, basket.getType());
		assertNull(basket.getBasketMatch());
		assertEquals(0, basket.getXpValue());
		assertNull(basket.getCompassAttemptId(), "the scientific profile was in force");
		assertEquals(List.of(), basket.getProducts());
		assertEquals(List.of(), basket.getEarnedBadges());
	}

	@Test
	void keepsThePurchaseTimeAsSentAndRecordsItAtTheServersTime() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD));
		givenThePurchaseIsStored(10, StreakEffect.EXTEND, STREAK + 1);
		var elsewhere = EARLIER.withOffsetSameInstant(ZoneOffset.ofHours(-7));

		purchase(elsewhere, List.of(GOOD));

		assertEquals(elsewhere, storedBasket.getValue().getPurchasedAt(), "the local time at the shop, with its offset");
		assertEquals(NOW, storedBasket.getValue().getRecordedAt());
	}

	/**
	 * A shop at +14:00 sends a purchase it made just now: its local time runs ahead of the server's clock by hours, its
	 * instant does not.
	 */
	@Test
	void acceptsAPurchaseMadeNowAtAShopAheadOfTheServersTimeZone() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD));
		givenThePurchaseIsStored(10, StreakEffect.EXTEND, STREAK + 1);
		var now = atServerZone(NOW).withOffsetSameInstant(ZoneOffset.ofHours(14));

		purchase(now, List.of(GOOD));

		assertEquals(now, storedBasket.getValue().getPurchasedAt(), "stored as sent");
	}

	@Test
	void acceptsAPurchaseTimeAheadOfTheServerClockByLessThanTheSkewAllowed() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD));
		givenThePurchaseIsStored(10, StreakEffect.EXTEND, STREAK + 1);
		var ahead = NOW.plus(BasketAssessmentServiceImpl.MAX_CLOCK_SKEW);

		purchase(atServerZone(ahead), List.of(GOOD));

		assertEquals(atServerZone(ahead), storedBasket.getValue().getPurchasedAt(), "stored as sent");
	}

	@Test
	void rejectsAPurchaseTimeInTheFuture() {
		when(dateTimeService.getNow()).thenReturn(NOW);
		var future = atServerZone(NOW.plus(BasketAssessmentServiceImpl.MAX_CLOCK_SKEW).plusSeconds(1));

		var e = assertThrows(BadInputValueException.class, () -> sut.assessPurchasedBasket(USER, CATALOGUE, future, List.of(GOOD), LANGUAGE));

		assertEquals("purchasedAt", e.getInputName());
		verifyNoInteractions(purchasedBasketDao, userProfileDao);
	}

	@Test
	void requiresTheCatalogueOfAPurchase() {
		var e = assertThrows(RequiredInputMissingException.class, () -> sut.assessPurchasedBasket(USER, null, EARLIER, List.of(GOOD), LANGUAGE));

		assertEquals("catalogueId", e.getInputName());
		verifyNoInteractions(purchasedBasketDao, userProfileDao);
	}

	@Test
	void requiresThePurchaseTime() {
		var e = assertThrows(RequiredInputMissingException.class, () -> sut.assessPurchasedBasket(USER, CATALOGUE, null, List.of(GOOD), LANGUAGE));

		assertEquals("purchasedAt", e.getInputName());
		verifyNoInteractions(purchasedBasketDao, userProfileDao);
	}

	/**
	 * {@link AssessmentInputsTest} tests the checks themselves.
	 */
	@Test
	void checksTheLanguageAndTheProductKeysOfAPurchase() {
		var e = assertThrows(RequiredInputMissingException.class, () -> sut.assessPurchasedBasket(USER, CATALOGUE, EARLIER, null, LANGUAGE));

		assertEquals("productKeys", e.getInputName());
		verifyNoInteractions(purchasedBasketDao, userProfileDao);
	}

	@Test
	void failsForACatalogueThatDoesNotExist() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD));
		when(dateTimeService.getNow()).thenReturn(NOW);
		when(userProfileDao.lock(any(), matchesHasUserId(USER))).thenReturn(Uni.createFrom().voidItem());
		when(catalogueDao.findRetailers(any(), eq(Set.of(CATALOGUE)))).thenReturn(Uni.createFrom().item(Map.of()));

		var failure = sut.assessPurchasedBasket(USER, CATALOGUE, EARLIER, List.of(GOOD), LANGUAGE)
				.onItem().<Throwable>transform(_ -> null)
				.onFailure().recoverWithItem(t -> t)
				.await().atMost(WAIT);

		assertInstanceOf(NotFoundException.class, failure);
		verifyNoInteractions(purchasedBasketDao);
		verify(userProfileDao).lock(any(), matchesHasUserId(USER));
		verifyNoMoreInteractions(userProfileDao);
	}

	private BasketAssessmentOutcome purchase(OffsetDateTime purchasedAt, List<ProductKey> productKeys) {
		return sut.assessPurchasedBasket(USER, CATALOGUE, purchasedAt, productKeys, LANGUAGE).await().atMost(WAIT);
	}

	private static OffsetDateTime atServerZone(LocalDateTime dateTime) {
		return dateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
	}

	/**
	 * The scores the product is stored with are its archetype's, in the corpus the basket was scored with.
	 */
	private static PurchasedBasketProduct product(ProductKey productKey, ArchetypeProductId archetypeProductId, WeightProfile personal) {
		var scoredProduct = ARCHETYPES.find(archetypeProductId).orElseThrow();
		return ImmutablePurchasedBasketProduct.builder()
				.productKey(productKey)
				.archetypeProductId(archetypeProductId)
				.personalOverallScore(scoredProduct.overallScore(personal))
				.scientificOverallScore(scoredProduct.overallScore(ScientificWeights.profile()))
				.normalisedScores(scoredProduct.normalisedScores())
				.build();
	}

	/**
	 * The user has nothing towards a badge.
	 */
	private void givenThePurchaseIsStored(int xpValue, StreakEffect streakEffect, int streakAfter) {
		givenThePurchaseIsStored(xpValue, streakEffect, streakAfter, NOTHING_COUNTED, Set.of());
	}

	/**
	 * The user's profile is locked, what the user has towards the badges is read, and the profile takes the change of the
	 * Streak the effect calls for, none for {@code NONE}, and reads back the XP and the Streak the basket leaves them with.
	 */
	private void givenThePurchaseIsStored(int xpValue, StreakEffect streakEffect, int streakAfter, Map<ScoredSustainabilityDimension, Long> storedCounts, Set<Badge> held) {
		when(dateTimeService.getNow()).thenReturn(NOW);
		when(userProfileDao.lock(any(), matchesHasUserId(USER))).thenReturn(Uni.createFrom().voidItem());
		givenTheBadgeStanding(storedCounts, held);
		when(catalogueDao.findRetailers(any(), eq(Set.of(CATALOGUE)))).thenReturn(Uni.createFrom().item(Map.of(CATALOGUE, RETAILER)));
		when(purchasedBasketDao.store(any(), storedBasket.capture())).thenReturn(Uni.createFrom().voidItem());
		when(userProfileDao.addXp(any(), matchesHasUserId(USER), eq(xpValue))).thenReturn(Uni.createFrom().voidItem());
		switch (streakEffect) {
			case EXTEND -> when(userProfileDao.increaseStreak(any(), matchesHasUserId(USER))).thenReturn(Uni.createFrom().voidItem());
			case RESET -> when(userProfileDao.resetStreak(any(), matchesHasUserId(USER))).thenReturn(Uni.createFrom().voidItem());
			case NONE -> { }
		}
		when(userProfileDao.requireXpAndStreak(any(), matchesHasUserId(USER))).thenReturn(Uni.createFrom().item(xpAndStreak(TOTAL_XP + xpValue, streakAfter)));
	}

	private void givenScientificWeightsWithoutAnAttempt() {
		when(personalWeightsProvider.forUser(USER)).thenReturn(Uni.createFrom().item(new WeightProfileAndAttemptId(ScientificWeights.profile(), null)));
	}

	private BasketAssessmentOutcome assess(List<ProductKey> productKeys) {
		return sut.assessBasketPrePurchase(USER, CATALOGUE, productKeys, LANGUAGE).await().atMost(WAIT);
	}

	private static void assertNothingToAssess(BasketAssessmentOutcome outcome) {
		assertEquals(NOTHING_TO_ASSESS, outcome.getType());
		assertNull(outcome.getBasketMatch());
		assertNull(outcome.getBasketMatchLabel());
		assertEquals(0, outcome.getXpValue());
		assertEquals(List.of(), outcome.getProblematicProductKeys());
		assertEquals(TOTAL_XP, outcome.getTotalXp());
		assertEquals(StreakEffect.NONE, outcome.getStreakEffect());
		assertEquals(STREAK, outcome.getStreak());
		assertEquals(List.of(), outcome.getEarnedBadges());
	}

	private void givenWeights(WeightProfile profile) {
		when(personalWeightsProvider.forUser(USER)).thenReturn(Uni.createFrom().item(new WeightProfileAndAttemptId(profile, ATTEMPT_ID)));
	}

	/**
	 * The products are identified once, by their distinct keys.
	 */
	private void givenIdentifications(Set<ProductKey> productKeys) {
		var identifications = new HashMap<ProductKey, ProductIdentification>();
		productKeys.forEach(productKey -> identifications.put(productKey, IDENTIFICATIONS.get(productKey)));
		when(referenceProductIdentifier.identify(CATALOGUE, productKeys)).thenReturn(Uni.createFrom().item(identifications));
		when(archetypeCorpus.scoredArchetypes()).thenReturn(Uni.createFrom().item(ARCHETYPES));
	}

	/**
	 * The user's XP total and Streak, and nothing towards a badge.
	 */
	private void givenTheUsersStanding() {
		givenTheUsersStanding(NOTHING_COUNTED, Set.of());
	}

	/**
	 * The user's XP total and Streak, and what they have towards the badges.
	 */
	private void givenTheUsersStanding(Map<ScoredSustainabilityDimension, Long> storedCounts, Set<Badge> held) {
		when(userProfileDao.requireXpAndStreak(any(), matchesHasUserId(USER))).thenReturn(Uni.createFrom().item(xpAndStreak(TOTAL_XP, STREAK)));
		givenTheBadgeStanding(storedCounts, held);
	}

	/**
	 * The user's stored products above 0.8 per dimension, and the badges they hold.
	 */
	private void givenTheBadgeStanding(Map<ScoredSustainabilityDimension, Long> storedCounts, Set<Badge> held) {
		when(purchasedBasketDao.retrieveProductCountsAbove(any(), matchesHasUserId(USER), eq(0.8))).thenReturn(Uni.createFrom().item(storedCounts));
		when(purchasedBasketDao.findBadges(any(), matchesHasUserId(USER))).thenReturn(Uni.createFrom().item(held));
	}

	private static Map<ScoredSustainabilityDimension, Long> counts(long environment, long animalWelfare, long socioEconomic, long health) {
		return Map.of(
				ScoredSustainabilityDimension.ENVIRONMENT, environment,
				ScoredSustainabilityDimension.ANIMAL_WELFARE, animalWelfare,
				ScoredSustainabilityDimension.SOCIO_ECONOMIC, socioEconomic,
				ScoredSustainabilityDimension.HEALTH, health);
	}

	private static EarnedBadge earned(Badge badge, String label) {
		return ImmutableEarnedBadge.builder().badge(badge).label(label).build();
	}

	private static XpAndStreak xpAndStreak(int xpTotal, int streak) {
		return ImmutableXpAndStreak.builder().xpTotal(xpTotal).streak(streak).build();
	}

	private static ProductIdentification identified(ArchetypeProductId archetypeProductId) {
		return new ProductIdentification.Identified(archetypeProductId, null);
	}

	private static ProductIdentification notIdentified(IdentificationFailureReason reason) {
		return new ProductIdentification.NotIdentified(reason, null);
	}
}
