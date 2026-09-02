package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIAL;
import static eu.tealhelix.sfc.v1.types.ScaleOption.EXTREMELY_IMPORTANT;
import static eu.tealhelix.sfc.v1.types.ScaleOption.MODERATELY_IMPORTANT;
import static eu.tealhelix.sfc.v1.types.ScaleOption.NOT_IMPORTANT;
import static eu.tealhelix.sfc.v1.types.ScaleOption.SLIGHTLY_IMPORTANT;
import static eu.tealhelix.sfc.v1.types.ScaleOption.VERY_IMPORTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import eu.tealhelix.sfc.services.v1.CompassReadService;
import eu.tealhelix.sfc.services.v1.types.CompletedCompassAnswers;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The compass answers below are chosen so the expected weights can be computed by hand: the mean of each dimension's
 * answers, the economic mean discarded, and the four that remain scaled to sum to one.
 */
@ExtendWith(MockitoExtension.class)
class PersonalWeightsProviderTest {
	private static final Duration WAIT = Duration.ofSeconds(300);
	private static final double TOLERANCE = 1e-9;

	private static final User USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, false);
	private static final UUID ATTEMPT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

	@Mock
	private CompassReadService compassReadService;

	@InjectMocks
	private PersonalWeightsProvider sut;

	@Test
	void weightsEachDimensionByTheMeanOfItsAnswers() {
		// means: ecological 4, social 2, health 1, animal welfare 1 — summing to 8.
		givenCompleted(Map.of(
				SustainabilityDimension.ECOLOGICAL, List.of(VERY_IMPORTANT, VERY_IMPORTANT),
				SustainabilityDimension.SOCIAL, List.of(NOT_IMPORTANT, MODERATELY_IMPORTANT),
				SustainabilityDimension.HEALTH, List.of(NOT_IMPORTANT),
				SustainabilityDimension.ANIMAL_WELFARE, List.of(NOT_IMPORTANT)));

		var weights = sut.forUser(USER).await().atMost(WAIT).getDimensionWeights();

		assertEquals(0.500, weights.get(ENVIRONMENT), TOLERANCE, "4 of 8; ecological and environment are one facet under two names");
		assertEquals(0.250, weights.get(SOCIAL), TOLERANCE, "2 of 8");
		assertEquals(0.125, weights.get(HEALTH), TOLERANCE, "1 of 8");
		assertEquals(0.125, weights.get(ANIMAL_WELFARE), TOLERANCE, "1 of 8");
	}

	@Test
	void discardsTheEconomicDimensionWhateverTheUserSaidAboutIt() {
		givenCompleted(withEconomic(EXTREMELY_IMPORTANT));
		var emphatic = sut.forUser(USER).await().atMost(WAIT).getDimensionWeights();

		givenCompleted(withEconomic(NOT_IMPORTANT));
		var indifferent = sut.forUser(USER).await().atMost(WAIT).getDimensionWeights();

		assertEquals(emphatic, indifferent,
				"the compass asks about the economy, but the product data carries no economic indicator to weight");
	}

	@Test
	void flattensToEqualWeightsWhenEveryAnswerIsTheSame() {
		assertEquals(evenWeights(), weightsForUniformAnswers(EXTREMELY_IMPORTANT), "all fives: no dimension above another");
		assertEquals(evenWeights(), weightsForUniformAnswers(MODERATELY_IMPORTANT), "all threes: the same statement, and deliberately the same weights");
	}

	@Test
	void fallsBackToTheScientificProfileWhenTheUserHasCompletedNoAttempt() {
		when(compassReadService.findLatestCompletedAnswers(USER)).thenReturn(Uni.createFrom().item(Optional.empty()));

		var profile = sut.forUser(USER).await().atMost(WAIT);

		assertEquals(ScientificWeights.profile().getDimensionWeights(), profile.getDimensionWeights(),
				"a user who has not taken the compass still gets all three recommendations");
	}

	@Test
	void fallsBackToTheScientificProfileWhenADimensionHasNoAnswersAtAll() {
		givenCompleted(Map.of(
				SustainabilityDimension.ECOLOGICAL, List.of(VERY_IMPORTANT),
				SustainabilityDimension.SOCIAL, List.of(VERY_IMPORTANT),
				SustainabilityDimension.HEALTH, List.of(VERY_IMPORTANT)));

		var profile = sut.forUser(USER).await().atMost(WAIT);

		assertEquals(ScientificWeights.profile().getDimensionWeights(), profile.getDimensionWeights(),
				"nothing was said about animal welfare, so there is no personal profile to derive");
	}

	@Test
	void keepsTheScientificWeightsWithinEachDimension() {
		givenCompleted(withEconomic(SLIGHTLY_IMPORTANT));

		var profile = sut.forUser(USER).await().atMost(WAIT);

		assertEquals(ScientificWeights.profile().getIndicatorWeights(), profile.getIndicatorWeights(),
				"the compass sets how the dimensions weigh against each other, nothing finer");
	}

	private Map<ScoredSustainabilityDimension, Double> weightsForUniformAnswers(ScaleOption everywhere) {
		givenCompleted(withEconomic(everywhere));
		return sut.forUser(USER).await().atMost(WAIT).getDimensionWeights();
	}

	private void givenCompleted(Map<SustainabilityDimension, List<ScaleOption>> answers) {
		when(compassReadService.findLatestCompletedAnswers(USER))
				.thenReturn(Uni.createFrom().item(Optional.of(new CompletedCompassAnswers(ATTEMPT_ID, answers))));
	}

	/** Every dimension answered the same, so only the economic answer distinguishes one call from another. */
	private static Map<SustainabilityDimension, List<ScaleOption>> withEconomic(ScaleOption economic) {
		var answers = new EnumMap<SustainabilityDimension, List<ScaleOption>>(SustainabilityDimension.class);
		answers.put(SustainabilityDimension.ECOLOGICAL, List.of(SLIGHTLY_IMPORTANT));
		answers.put(SustainabilityDimension.SOCIAL, List.of(SLIGHTLY_IMPORTANT));
		answers.put(SustainabilityDimension.HEALTH, List.of(SLIGHTLY_IMPORTANT));
		answers.put(SustainabilityDimension.ANIMAL_WELFARE, List.of(SLIGHTLY_IMPORTANT));
		answers.put(SustainabilityDimension.ECONOMIC, List.of(economic));
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
