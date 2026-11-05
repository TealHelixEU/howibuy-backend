package eu.tealhelix.howibuy.services.v1.impl;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.scoring.v1.ScientificWeights;
import eu.tealhelix.howibuy.v1.types.ImmutableWeightProfile;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import eu.tealhelix.sfc.services.v1.CompassReadService;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;
import io.smallrye.mutiny.Uni;

/**
 * Turns what a user said on the Sustainable Food Compass into the weights their recommendations are ranked by.
 * <p>
 * The only place that knows the compass and the scoring engine both exist. Everything downstream sees a
 * {@link WeightProfile} and has no idea a questionnaire was involved.
 * <p>
 * Each answer counts from 0 for not important up to 4 for extremely important, and a dimension of the compass weighs
 * the mean of the answers given about it. A scored dimension weighs the mean of the compass dimensions it covers, and
 * the four are scaled to sum to one. WP3's notebook takes the weights from 100 points a respondent splits across the
 * five dimensions, a question the compass does not ask; WP3 agreed to this in its place.
 * <p>
 * Scaling makes intensity invisible on purpose: someone who answers 5 to everything and someone who answers 3 to
 * everything have both said "no dimension above another", and rank every product identically.
 */
@ApplicationScoped
public class PersonalWeightsProvider {
	/**
	 * The compass dimensions each of the four scored ones covers. Ecological and environment are one facet under two
	 * names, and socio-economic covers social and economic both, as WP3's socio-economic indicators include economic
	 * ones such as a fair salary (ADR 0005).
	 */
	private static final Map<ScoredSustainabilityDimension, List<SustainabilityDimension>> COVERED_DIMENSIONS = coveredDimensions();

	private final CompassReadService compassReadService;

	@Inject
	public PersonalWeightsProvider(CompassReadService compassReadService) {
		this.compassReadService = compassReadService;
	}

	/**
	 * The user's weighting profile, or the scientific one if they have never completed an attempt — so the
	 * recommendations a user gets before they have answered anything are still grounded, just not yet theirs. The
	 * scientific one also stands in when the latest completed attempt leaves a dimension of the compass unanswered.
	 */
	public Uni<WeightProfileAndAttemptId> forUser(User user) {
		return compassReadService.findLatestCompletedAnswers(user)
				.map(answers -> answers
						.flatMap(completed -> derive(completed.answersByDimension())
								.map(profile -> new WeightProfileAndAttemptId(profile, completed.attemptId())))
						.orElseGet(() -> new WeightProfileAndAttemptId(ScientificWeights.profile(), null)));
	}

	/**
	 * The compass moves the four dimension weights and nothing finer, as WP3 confirmed: it has no instrument at the
	 * resolution of a single PEF impact category or socio-economic indicator.
	 * <p>
	 * A user who said every dimension is not important has put none above another, and gets equal weights.
	 */
	private static Optional<WeightProfile> derive(Map<SustainabilityDimension, List<ScaleOption>> answersByDimension) {
		var importance = new EnumMap<SustainabilityDimension, Double>(SustainabilityDimension.class);
		for (var dimension : SustainabilityDimension.values()) {
			List<ScaleOption> answers = answersByDimension.getOrDefault(dimension, List.of());
			if (answers.isEmpty()) return Optional.empty();
			importance.put(dimension, answers.stream().mapToInt(PersonalWeightsProvider::importance).average().orElseThrow());
		}

		var scoredImportance = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		COVERED_DIMENSIONS.forEach((scored, covered) ->
				scoredImportance.put(scored, covered.stream().mapToDouble(importance::get).average().orElseThrow()));

		double total = scoredImportance.values().stream().mapToDouble(Double::doubleValue).sum();
		var weights = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		scoredImportance.forEach((dimension, value) -> weights.put(dimension, total == 0.0 ? 1.0 / scoredImportance.size() : value / total));

		return Optional.of(ImmutableWeightProfile.builder()
				.dimensionWeights(weights)
				.build());
	}

	/**
	 * How much an answer weighs: not important nothing, and every step up the scale one more.
	 */
	private static int importance(ScaleOption answer) {
		return answer.getValue() - ScaleOption.NOT_IMPORTANT.getValue();
	}

	private static Map<ScoredSustainabilityDimension, List<SustainabilityDimension>> coveredDimensions() {
		var covered = new EnumMap<ScoredSustainabilityDimension, List<SustainabilityDimension>>(ScoredSustainabilityDimension.class);
		covered.put(ScoredSustainabilityDimension.ENVIRONMENT, List.of(SustainabilityDimension.ECOLOGICAL));
		covered.put(ScoredSustainabilityDimension.ANIMAL_WELFARE, List.of(SustainabilityDimension.ANIMAL_WELFARE));
		covered.put(ScoredSustainabilityDimension.SOCIO_ECONOMIC, List.of(SustainabilityDimension.SOCIAL, SustainabilityDimension.ECONOMIC));
		covered.put(ScoredSustainabilityDimension.HEALTH, List.of(SustainabilityDimension.HEALTH));
		return Collections.unmodifiableMap(covered);
	}
}
