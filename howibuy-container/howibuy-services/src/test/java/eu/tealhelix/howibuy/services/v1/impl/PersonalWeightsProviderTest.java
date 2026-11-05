package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIO_ECONOMIC;
import static eu.tealhelix.sfc.v1.types.ScaleOption.EXTREMELY_IMPORTANT;
import static eu.tealhelix.sfc.v1.types.ScaleOption.MODERATELY_IMPORTANT;
import static eu.tealhelix.sfc.v1.types.ScaleOption.NOT_IMPORTANT;
import static eu.tealhelix.sfc.v1.types.ScaleOption.SLIGHTLY_IMPORTANT;
import static eu.tealhelix.sfc.v1.types.ScaleOption.VERY_IMPORTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.sfc.services.v1.CompassReadService;
import eu.tealhelix.sfc.services.v1.types.CompletedCompassAnswers;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The compass answers below are chosen so the expected weights can be computed by hand: each answer counts 0 for not
 * important up to 4 for extremely important, each dimension takes the mean of its answers, social and economic are
 * averaged into socio-economic, and the four scaled to sum to one.
 */
@ExtendWith(MockitoExtension.class)
class PersonalWeightsProviderTest {
	private static final Duration WAIT = Duration.ofSeconds(300);
	private static final double TOLERANCE = 1e-9;

	private static final User USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, true, Set.of());
	private static final UUID ATTEMPT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

	@Mock
	private CompassReadService compassReadService;

	@InjectMocks
	private PersonalWeightsProvider sut;

	@Test
	void weightsEachDimensionByTheMeanOfItsAnswersCountedFromZero() {
		// ecological 3, social 1 and economic 3 so socio-economic 2, health 2, animal welfare 1 — summing to 8.
		givenCompleted(Map.of(
				SustainabilityDimension.ECOLOGICAL, List.of(VERY_IMPORTANT, VERY_IMPORTANT),
				SustainabilityDimension.SOCIAL, List.of(NOT_IMPORTANT, MODERATELY_IMPORTANT),
				SustainabilityDimension.ECONOMIC, List.of(VERY_IMPORTANT),
				SustainabilityDimension.HEALTH, List.of(MODERATELY_IMPORTANT),
				SustainabilityDimension.ANIMAL_WELFARE, List.of(SLIGHTLY_IMPORTANT)));

		Map<ScoredSustainabilityDimension, Double> weights = weightsOfTheUser();

		assertEquals(0.375, weights.get(ENVIRONMENT), TOLERANCE, "3 of 8; ecological and environment are one facet under two names");
		assertEquals(0.250, weights.get(SOCIO_ECONOMIC), TOLERANCE, "2 of 8, the mean of social's 1 and economic's 3");
		assertEquals(0.250, weights.get(HEALTH), TOLERANCE, "2 of 8");
		assertEquals(0.125, weights.get(ANIMAL_WELFARE), TOLERANCE, "1 of 8: slightly important is half of moderately important");
	}

	@Test
	void averagesTheSocialAndTheEconomicAnswersIntoSocioEconomic() {
		givenCompleted(Map.of(
				SustainabilityDimension.ECOLOGICAL, List.of(MODERATELY_IMPORTANT),
				SustainabilityDimension.SOCIAL, List.of(NOT_IMPORTANT),
				SustainabilityDimension.ECONOMIC, List.of(EXTREMELY_IMPORTANT),
				SustainabilityDimension.HEALTH, List.of(MODERATELY_IMPORTANT),
				SustainabilityDimension.ANIMAL_WELFARE, List.of(MODERATELY_IMPORTANT)));

		assertEquals(evenWeights(), weightsOfTheUser(), "social's 0 and economic's 4 average to 2, as much as every other dimension");
	}

	@Test
	void weighsNothingADimensionEveryAnswerOfWhichIsNotImportant() {
		givenCompleted(Map.of(
				SustainabilityDimension.ECOLOGICAL, List.of(EXTREMELY_IMPORTANT),
				SustainabilityDimension.SOCIAL, List.of(EXTREMELY_IMPORTANT),
				SustainabilityDimension.ECONOMIC, List.of(EXTREMELY_IMPORTANT),
				SustainabilityDimension.HEALTH, List.of(EXTREMELY_IMPORTANT),
				SustainabilityDimension.ANIMAL_WELFARE, List.of(NOT_IMPORTANT, NOT_IMPORTANT)));

		Map<ScoredSustainabilityDimension, Double> weights = weightsOfTheUser();

		assertEquals(0.0, weights.get(ANIMAL_WELFARE), "not important at all");
		assertEquals(1.0 / 3.0, weights.get(ENVIRONMENT), TOLERANCE);
		assertEquals(1.0 / 3.0, weights.get(SOCIO_ECONOMIC), TOLERANCE);
		assertEquals(1.0 / 3.0, weights.get(HEALTH), TOLERANCE);
	}

	@Test
	void flattensToEqualWeightsWhenEveryAnswerIsTheSame() {
		assertEquals(evenWeights(), weightsForUniformAnswers(EXTREMELY_IMPORTANT), "all fives: no dimension above another");
		assertEquals(evenWeights(), weightsForUniformAnswers(MODERATELY_IMPORTANT), "all threes: the same statement, and deliberately the same weights");
	}

	@Test
	void flattensToEqualWeightsDerivedFromTheAttemptWhenEveryAnswerIsNotImportant() {
		givenCompleted(uniformAnswers(NOT_IMPORTANT));

		var weights = sut.forUser(USER).await().atMost(WAIT);

		assertEquals(evenWeights(), weights.profile().getDimensionWeights(), "nothing said above anything else, as when every answer is the same");
		assertEquals(ATTEMPT_ID, weights.compassAttemptId(), "the user did answer, so the weights are theirs");
	}

	@Test
	void fallsBackToTheScientificProfileWhenTheUserHasCompletedNoAttempt() {
		when(compassReadService.findLatestCompletedAnswers(USER)).thenReturn(Uni.createFrom().item(Optional.empty()));

		var weights = sut.forUser(USER).await().atMost(WAIT);

		assertEquals(ScientificWeights.profile().getDimensionWeights(), weights.profile().getDimensionWeights(),
				"a user who has not taken the compass is still recommended products");
		assertNull(weights.compassAttemptId(), "no attempt the profile was derived from");
	}

	@ParameterizedTest
	@EnumSource(SustainabilityDimension.class)
	void fallsBackToTheScientificProfileWhenADimensionHasNoAnswersAtAll(SustainabilityDimension unanswered) {
		var answers = distinctAnswers();
		answers.remove(unanswered);
		givenCompleted(answers);

		var weights = sut.forUser(USER).await().atMost(WAIT);

		assertEquals(ScientificWeights.profile().getDimensionWeights(), weights.profile().getDimensionWeights(),
				"nothing was said about " + unanswered + ", so there is no personal profile to derive");
		assertNull(weights.compassAttemptId(), "the attempt exists, but the profile was not derived from it");
	}

	@Test
	void fallsBackToTheScientificProfileWhenADimensionHasAnEmptyListOfAnswers() {
		var answers = distinctAnswers();
		answers.put(SustainabilityDimension.ECONOMIC, List.of());
		givenCompleted(answers);

		var weights = sut.forUser(USER).await().atMost(WAIT);

		assertEquals(ScientificWeights.profile().getDimensionWeights(), weights.profile().getDimensionWeights());
		assertNull(weights.compassAttemptId());
	}

	@Test
	void reportsTheAttemptThePersonalProfileWasDerivedFrom() {
		givenCompleted(distinctAnswers());

		assertEquals(ATTEMPT_ID, sut.forUser(USER).await().atMost(WAIT).compassAttemptId());
	}

	private Map<ScoredSustainabilityDimension, Double> weightsOfTheUser() {
		return sut.forUser(USER).await().atMost(WAIT).profile().getDimensionWeights();
	}

	private Map<ScoredSustainabilityDimension, Double> weightsForUniformAnswers(ScaleOption everywhere) {
		givenCompleted(uniformAnswers(everywhere));
		return weightsOfTheUser();
	}

	private void givenCompleted(Map<SustainabilityDimension, List<ScaleOption>> answers) {
		when(compassReadService.findLatestCompletedAnswers(USER))
				.thenReturn(Uni.createFrom().item(Optional.of(new CompletedCompassAnswers(ATTEMPT_ID, answers))));
	}

	private static Map<SustainabilityDimension, List<ScaleOption>> uniformAnswers(ScaleOption everywhere) {
		var answers = new EnumMap<SustainabilityDimension, List<ScaleOption>>(SustainabilityDimension.class);
		for (var dimension : SustainabilityDimension.values()) {
			answers.put(dimension, List.of(everywhere, everywhere));
		}
		return answers;
	}

	/**
	 * Answers that set every dimension apart from every other, so that weights derived from any four of them could not
	 * pass for equal ones.
	 */
	private static Map<SustainabilityDimension, List<ScaleOption>> distinctAnswers() {
		var answers = new EnumMap<SustainabilityDimension, List<ScaleOption>>(SustainabilityDimension.class);
		answers.put(SustainabilityDimension.ECOLOGICAL, List.of(EXTREMELY_IMPORTANT));
		answers.put(SustainabilityDimension.SOCIAL, List.of(VERY_IMPORTANT));
		answers.put(SustainabilityDimension.ECONOMIC, List.of(NOT_IMPORTANT));
		answers.put(SustainabilityDimension.HEALTH, List.of(SLIGHTLY_IMPORTANT));
		answers.put(SustainabilityDimension.ANIMAL_WELFARE, List.of(MODERATELY_IMPORTANT));
		return answers;
	}

	private static Map<ScoredSustainabilityDimension, Double> evenWeights() {
		var weights = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		for (var dimension : ScoredSustainabilityDimension.values()) {
			weights.put(dimension, 0.25);
		}
		return weights;
	}
}
