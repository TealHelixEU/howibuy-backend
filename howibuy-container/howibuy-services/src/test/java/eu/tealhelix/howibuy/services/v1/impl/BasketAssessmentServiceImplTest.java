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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
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
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.scoring.v1.SubstitutionSettings;
import eu.tealhelix.howibuy.services.v1.impl.ArchetypeImpactsFixtures.Branch;
import eu.tealhelix.howibuy.v1.model.BasketAssessmentOutcome;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.BasketGrade;
import eu.tealhelix.howibuy.v1.types.IdentificationFailureReason;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
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

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	private BasketAssessmentServiceImpl sut;

	@BeforeEach
	void setUp() {
		sut = new BasketAssessmentServiceImpl(
				new TealHelixAuthorizationImpl(), referenceProductIdentifier, archetypeCorpus, personalWeightsProvider,
				persistenceContextFactory, userProfileDao);
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
