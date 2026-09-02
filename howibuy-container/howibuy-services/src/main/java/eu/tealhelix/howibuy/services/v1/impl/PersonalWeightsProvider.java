package eu.tealhelix.howibuy.services.v1.impl;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * A dimension's weight is the mean of the 1–5 answers given about it, with the four scored dimensions scaled to sum to
 * one. Scaling makes intensity invisible on purpose: someone who answers 5 to everything and someone who answers 3 to
 * everything have both said "no dimension above another", and rank every product identically.
 */
@ApplicationScoped
public class PersonalWeightsProvider {
	/**
	 * The compass's five dimensions against the four an archetype product is scored on. Ecological and environment are
	 * one facet under two names. Economic maps to nothing: the compass asks about it, and the WP3 product data carries
	 * no economic indicator to weight, so the answer is elicited and discarded (ADR 0005).
	 */
	private static final Map<SustainabilityDimension, ScoredSustainabilityDimension> SCORED_DIMENSIONS = scoredDimensions();

	private final CompassReadService compassReadService;

	@Inject
	public PersonalWeightsProvider(CompassReadService compassReadService) {
		this.compassReadService = compassReadService;
	}

	/**
	 * The user's weighting profile, or the scientific one if they have never completed an attempt — so the
	 * recommendations a user gets before they have answered anything are still grounded, just not yet theirs.
	 */
	public Uni<WeightProfile> forUser(User user) {
		return compassReadService.findLatestCompletedAnswers(user)
				.map(answers -> answers
						.map(completed -> derive(completed.answersByDimension()))
						.orElseGet(ScientificWeights::profile));
	}

	/**
	 * The compass moves the four dimension weights and nothing finer: the indicator weights stay at their scientific
	 * values for every user. That is an assumption about the method's intended granularity, pending question 2.1 to KU
	 * Leuven — the compass has no instrument at the resolution of a single PEF impact category or social indicator, and
	 * WP3's own sensitivity analysis sweeps only the four dimension weights.
	 * <p>
	 * Should the answer be that personalisation reaches inside a dimension, {@link WeightProfile} already carries both
	 * levels, so the change lands here rather than in the engine. It would cost more than an edit, though: with the
	 * indicator weights fixed, every raw single score and the whole min–max normalisation are user-independent and the
	 * corpus can be scored once, which is what {@link eu.tealhelix.howibuy.scoring.v1.ScoredCorpus} relies on.
	 */
	private static WeightProfile derive(Map<SustainabilityDimension, List<ScaleOption>> answersByDimension) {
		var means = meanAnswerPerScoredDimension(answersByDimension);
		if (means.size() != ScoredSustainabilityDimension.values().length) return ScientificWeights.profile();

		var total = means.values().stream().mapToDouble(Double::doubleValue).sum();
		var weights = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		means.forEach((dimension, mean) -> weights.put(dimension, mean / total));

		return ImmutableWeightProfile.builder()
				.indicatorWeights(ScientificWeights.profile().getIndicatorWeights())
				.dimensionWeights(weights)
				.build();
	}

	/**
	 * A dimension nobody answered about carries no mean and is simply absent, which is what the caller reads as "there
	 * is no personal profile to derive here". The total can never be zero, because the lowest thing a user can say
	 * about a dimension is still a 1.
	 */
	private static Map<ScoredSustainabilityDimension, Double> meanAnswerPerScoredDimension(
			Map<SustainabilityDimension, List<ScaleOption>> answersByDimension) {
		var means = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		for (var answers : answersByDimension.entrySet()) {
			var scored = SCORED_DIMENSIONS.get(answers.getKey());
			if (scored == null || answers.getValue().isEmpty()) continue;
			means.put(scored, answers.getValue().stream().mapToInt(ScaleOption::getValue).average().orElseThrow());
		}
		return means;
	}

	private static Map<SustainabilityDimension, ScoredSustainabilityDimension> scoredDimensions() {
		var scored = new EnumMap<SustainabilityDimension, ScoredSustainabilityDimension>(SustainabilityDimension.class);
		scored.put(SustainabilityDimension.ECOLOGICAL, ScoredSustainabilityDimension.ENVIRONMENT);
		scored.put(SustainabilityDimension.SOCIAL, ScoredSustainabilityDimension.SOCIAL);
		scored.put(SustainabilityDimension.HEALTH, ScoredSustainabilityDimension.HEALTH);
		scored.put(SustainabilityDimension.ANIMAL_WELFARE, ScoredSustainabilityDimension.ANIMAL_WELFARE);
		return Map.copyOf(scored);
	}
}
