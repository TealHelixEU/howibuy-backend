package eu.tealhelix.howibuy.services.v1.impl;

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
import static org.mockito.Mockito.verifyNoInteractions;
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
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.CatalogueDao;
import eu.tealhelix.howibuy.dao.PurchasedBasketDao;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.scoring.v1.SubstitutionSettings;
import eu.tealhelix.howibuy.services.model.ImmutablePurchasedBasket;
import eu.tealhelix.howibuy.services.model.ImmutablePurchasedBasketProduct;
import eu.tealhelix.howibuy.services.model.PurchasedBasket;
import eu.tealhelix.howibuy.services.model.PurchasedBasketProduct;
import eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.Branch;
import eu.tealhelix.howibuy.v1.model.BasketAssessmentOutcome;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.BasketGrade;
import eu.tealhelix.howibuy.v1.types.IdentificationFailureReason;
import eu.tealhelix.howibuy.v1.types.ProductGrade;
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
 * {@link ArchetypeImpactsFixtures} so that each Overall Score falls in a known Product Grade. The corpus spans 0 to 1
 * in every dimension, so the normalised scores below are exact:
 *
 * <pre>
 *            E     AW    S     H     scientific  environment only
 * Good       1.0   1.0   1.0   1.0   1.0  (A)    1.0  (A)
 * Fair       0.75  0.75  0.75  0.75  0.75 (B)    0.75 (B)
 * Poor       0.0   0.0   0.0   0.0   0.0  (C)    0.0  (C)
 * Green      1.0   0.0   0.0   0.0   0.25 (C)    1.0  (A)
 * Spirits    —     —     —     —     none        none
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class BasketAssessmentServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final User USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, true, Set.of());
	private static final User SERVICE_USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, false, Set.of());
	private static final CatalogueId CATALOGUE = new CatalogueIdImpl("test-gr");
	private static final String LANGUAGE = "en";
	private static final int TOTAL_XP = 17;
	private static final UUID ATTEMPT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final RetailerId RETAILER = new GenericRetailerId("8d1e5b4a-7c2f-4e93-a6b0-3f9d2c1e7a54");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0);
	private static final LocalDateTime EARLIER = NOW.minusHours(2);

	private static final Branch DAIRY = new Branch("Dairy", "Milk", "Cow milk");
	private static final ArchetypeProductId GOOD_ARCHETYPE = productId("good");
	private static final ArchetypeProductId FAIR_ARCHETYPE = productId("fair");
	private static final ArchetypeProductId POOR_ARCHETYPE = productId("poor");
	private static final ArchetypeProductId GREEN_ARCHETYPE = productId("green");
	private static final ArchetypeProductId SPIRITS_ARCHETYPE = productId("spirits");
	private static final ScoredArchetypes ARCHETYPES = ScoredArchetypes.of(
			List.of(
					impacts(GOOD_ARCHETYPE, "Good", "100", DAIRY, 0.0, 0.0, 0.0, "Nutriscore_A"),
					impacts(FAIR_ARCHETYPE, "Fair", "200", DAIRY, 0.25, 0.25, 0.25, "Nutriscore_B"),
					impacts(POOR_ARCHETYPE, "Poor", "300", DAIRY, 1.0, 1.0, 1.0, "Nutriscore_E"),
					impacts(GREEN_ARCHETYPE, "Green", "400", DAIRY, 0.0, 1.0, 1.0, "Nutriscore_E"),
					impacts(SPIRITS_ARCHETYPE, "Spirits", "500", DAIRY, 0.5, 0.5, 0.5, "0")),
			List.of(), SubstitutionSettings.defaults());

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

	@Test
	void judgesTheBasketByTheOverallScoresOfItsProducts() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD, FAIR, POOR));
		givenTotalXp();

		var outcome = assess(List.of(FAIR, GOOD, POOR));

		assertEquals(ASSESSED, outcome.getType());
		assertEquals(BasketGrade.C, outcome.getBasketGrade(), "the worst Product Grade of the basket");
		assertEquals("Basket to improve", outcome.getBasketGradeLabel());
		assertEquals(3 + 1, outcome.getXpValue(), "an A and a B");
		assertEquals(List.of(POOR, FAIR), outcome.getProblematicProductKeys(), "below A, lowest Overall Score first");
		assertEquals(TOTAL_XP, outcome.getTotalXp(), "the user's total as it stands: nothing is stored before the purchase");
		assertNull(outcome.getIntervention());
	}

	@Test
	void leavesOutTheProductsThatHaveNoOverallScore() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD, UNKNOWN, UNCLASSIFIED, NO_ARCHETYPE, SPIRITS));
		givenTotalXp();

		var outcome = assess(List.of(GOOD, UNKNOWN, UNCLASSIFIED, NO_ARCHETYPE, SPIRITS));

		assertEquals(ASSESSED, outcome.getType());
		assertEquals(BasketGrade.A, outcome.getBasketGrade(), "only the good product counts");
		assertEquals(3, outcome.getXpValue());
		assertEquals(List.of(), outcome.getProblematicProductKeys(), "a product that cannot be judged does not count against the user");
	}

	@Test
	void countsARepeatedProductOnce() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(FAIR));
		givenTotalXp();

		var outcome = assess(List.of(FAIR, FAIR, FAIR));

		assertEquals(1, outcome.getXpValue(), "one B");
		assertEquals(List.of(FAIR), outcome.getProblematicProductKeys());
	}

	@Test
	void gradesUnderTheUsersPersonalWeights() {
		givenWeights(personalProfileOnEnvironment());
		givenIdentifications(Set.of(GREEN));
		givenTotalXp();

		var outcome = assess(List.of(GREEN));

		assertEquals(BasketGrade.A, outcome.getBasketGrade(), "a C under the scientific weights, an A for a user who cares only about the environment");
	}

	@Test
	void hasNothingToAssessWhenNoProductHasAnOverallScore() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(UNKNOWN, SPIRITS));
		givenTotalXp();

		assertNothingToAssess(assess(List.of(UNKNOWN, SPIRITS)));
	}

	@Test
	void hasNothingToAssessInAnEmptyBasket() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of());
		givenTotalXp();

		assertNothingToAssess(assess(List.of()));
	}

	@Test
	void rejectsAServiceUserRegisteringAPurchase() {
		assertThrows(NotAuthorizedException.class, () -> sut.assessPurchasedBasket(SERVICE_USER, CATALOGUE, null, List.of(GOOD), LANGUAGE));
	}

	@Test
	void storesThePurchasedBasketWithItsScoredProducts() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD, FAIR, POOR, UNKNOWN));
		givenThePurchaseIsStored(4);

		var outcome = purchase(atServerZone(EARLIER), List.of(FAIR, GOOD, POOR, UNKNOWN));

		assertEquals(ASSESSED, outcome.getType());
		assertEquals(BasketGrade.C, outcome.getBasketGrade());
		assertEquals(4, outcome.getXpValue());
		assertEquals(List.of(POOR, FAIR), outcome.getProblematicProductKeys());
		assertEquals(
				ImmutablePurchasedBasket.builder()
						.userId(USER.getId())
						.retailerId(RETAILER)
						.catalogueId(CATALOGUE)
						.purchasedAt(EARLIER)
						.recordedAt(NOW)
						.type(ASSESSED)
						.basketGrade(BasketGrade.C)
						.xpValue(4)
						.compassAttemptId(ATTEMPT_ID)
						.products(List.of(
								product(FAIR, FAIR_ARCHETYPE, ProductGrade.B),
								product(GOOD, GOOD_ARCHETYPE, ProductGrade.A),
								product(POOR, POOR_ARCHETYPE, ProductGrade.C)))
						.build(),
				storedBasket.getValue(),
				"the scored products in the order sent, the unknown one left out");
	}

	@Test
	void addsTheXpOfTheBasketToTheUsersTotalBeforeReadingIt() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD, FAIR));
		givenThePurchaseIsStored(4);

		var outcome = purchase(null, List.of(GOOD, FAIR));

		assertEquals(TOTAL_XP + 4, outcome.getTotalXp(), "the total includes this basket");
		var order = inOrder(purchasedBasketDao, userProfileDao);
		order.verify(purchasedBasketDao).store(any(), any());
		order.verify(userProfileDao).addXp(any(), eq(USER.getId()), eq(4));
		order.verify(userProfileDao).requireXpTotal(any(), eq(USER.getId()));
	}

	@Test
	void storesAPurchaseWithNothingToAssessWithoutProducts() {
		givenScientificWeightsWithoutAnAttempt();
		givenIdentifications(Set.of());
		givenThePurchaseIsStored(0);

		var outcome = purchase(null, List.of());

		assertEquals(NOTHING_TO_ASSESS, outcome.getType());
		assertEquals(TOTAL_XP, outcome.getTotalXp());
		var basket = storedBasket.getValue();
		assertEquals(NOTHING_TO_ASSESS, basket.getType());
		assertNull(basket.getBasketGrade());
		assertEquals(0, basket.getXpValue());
		assertNull(basket.getCompassAttemptId(), "the scientific profile was in force");
		assertEquals(List.of(), basket.getProducts());
	}

	@Test
	void takesThePurchaseTimeToBeTheTimeOfTheRequestWhenNotGiven() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD));
		givenThePurchaseIsStored(3);

		purchase(null, List.of(GOOD));

		assertEquals(NOW, storedBasket.getValue().getPurchasedAt());
		assertEquals(NOW, storedBasket.getValue().getRecordedAt());
	}

	@Test
	void keepsThePurchaseTimeInTheTimeZoneOfTheServer() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD));
		givenThePurchaseIsStored(3);
		var elsewhere = atServerZone(EARLIER).withOffsetSameInstant(ZoneOffset.ofHours(-7));

		purchase(elsewhere, List.of(GOOD));

		assertEquals(EARLIER, storedBasket.getValue().getPurchasedAt(), "the same instant, as the server tells the time");
	}

	@Test
	void acceptsAPurchaseTimeAheadOfTheServerClockByLessThanTheSkewAllowed() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD));
		givenThePurchaseIsStored(3);
		var ahead = NOW.plus(BasketAssessmentServiceImpl.MAX_CLOCK_SKEW);

		purchase(atServerZone(ahead), List.of(GOOD));

		assertEquals(ahead, storedBasket.getValue().getPurchasedAt(), "stored as sent");
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
	void failsForACatalogueThatDoesNotExist() {
		givenWeights(ScientificWeights.profile());
		givenIdentifications(Set.of(GOOD));
		when(dateTimeService.getNow()).thenReturn(NOW);
		when(catalogueDao.findRetailers(any(), eq(Set.of(CATALOGUE)))).thenReturn(Uni.createFrom().item(Map.of()));

		var failure = sut.assessPurchasedBasket(USER, CATALOGUE, null, List.of(GOOD), LANGUAGE)
				.onItem().<Throwable>transform(_ -> null)
				.onFailure().recoverWithItem(t -> t)
				.await().atMost(WAIT);

		assertInstanceOf(NotFoundException.class, failure);
		verifyNoInteractions(purchasedBasketDao, userProfileDao);
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
	private static PurchasedBasketProduct product(ProductKey productKey, ArchetypeProductId archetypeProductId, ProductGrade productGrade) {
		var scoredProduct = ARCHETYPES.find(archetypeProductId).orElseThrow();
		return ImmutablePurchasedBasketProduct.builder()
				.productKey(productKey)
				.archetypeProductId(archetypeProductId)
				.overallScore(scoredProduct.overallScore(ScientificWeights.profile()))
				.productGrade(productGrade)
				.normalisedScores(scoredProduct.normalisedScores())
				.build();
	}

	private void givenThePurchaseIsStored(int xpValue) {
		when(dateTimeService.getNow()).thenReturn(NOW);
		when(catalogueDao.findRetailers(any(), eq(Set.of(CATALOGUE)))).thenReturn(Uni.createFrom().item(Map.of(CATALOGUE, RETAILER)));
		when(purchasedBasketDao.store(any(), storedBasket.capture())).thenReturn(Uni.createFrom().voidItem());
		when(userProfileDao.addXp(any(), eq(USER.getId()), eq(xpValue))).thenReturn(Uni.createFrom().voidItem());
		when(userProfileDao.requireXpTotal(any(), eq(USER.getId()))).thenReturn(Uni.createFrom().item(TOTAL_XP + xpValue));
	}

	private void givenScientificWeightsWithoutAnAttempt() {
		when(personalWeightsProvider.forUser(USER)).thenReturn(Uni.createFrom().item(new WeightProfileAndAttemptId(ScientificWeights.profile(), null)));
	}

	private BasketAssessmentOutcome assess(List<ProductKey> productKeys) {
		return sut.assessBasketPrePurchase(USER, CATALOGUE, productKeys, LANGUAGE).await().atMost(WAIT);
	}

	private static void assertNothingToAssess(BasketAssessmentOutcome outcome) {
		assertEquals(NOTHING_TO_ASSESS, outcome.getType());
		assertNull(outcome.getBasketGrade());
		assertNull(outcome.getBasketGradeLabel());
		assertEquals(0, outcome.getXpValue());
		assertEquals(List.of(), outcome.getProblematicProductKeys());
		assertEquals(TOTAL_XP, outcome.getTotalXp());
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

	private void givenTotalXp() {
		when(userProfileDao.requireXpTotal(any(), eq(USER.getId()))).thenReturn(Uni.createFrom().item(TOTAL_XP));
	}

	private static ProductIdentification identified(ArchetypeProductId archetypeProductId) {
		return new ProductIdentification.Identified(archetypeProductId, null);
	}

	private static ProductIdentification notIdentified(IdentificationFailureReason reason) {
		return new ProductIdentification.NotIdentified(reason, null);
	}
}
