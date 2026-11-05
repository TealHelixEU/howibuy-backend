package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.HEALTH;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIO_ECONOMIC;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.ANIMAL_WELFARE_INDEX;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.ANTIBIOTIC_INDEX;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CARCINOGENIC_TOXICITY;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.CLIMATE_CHANGE;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.ENERGY_USE;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.FRESHWATER_ECOTOXICITY;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.FRESHWATER_EUTROPHICATION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.IONIZING_RADIATION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.LAND_USE;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.LAND_WATER_ACIDIFICATION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.MARINE_EUTROPHICATION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.MINERAL_USE;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.NON_CARCINOGENIC_TOXICITY;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.OZONE_DEPLETION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.OZONE_FORMATION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.PARTICULATE_MATTER;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.TERRESTRIAL_EUTROPHICATION;
import static eu.tealhelix.howibuy.v1.types.SustainabilityIndicator.WATER_USE;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import eu.tealhelix.howibuy.v1.types.ImmutableWeightProfile;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;
import eu.tealhelix.howibuy.v1.types.WeightProfile;

/**
 * WP3's expert weights: those of the indicators within each dimension, which every product's single scores are taken
 * with regardless of who is asking, and the scientific profile, the baseline a personalised recommendation may never
 * fall below.
 * <p>
 * Transcribed from {@code E_scientific_weights}, {@code AW_scientific_weights}, {@code S_scientific_weights} and
 * {@code overall_scientific_weights} in {@code TH_Algorithm_Implementation_v2026-05-20.Rmd}.
 */
public final class ScientificWeights {
	private static final Map<SustainabilityIndicator, Double> INDICATOR_WEIGHTS = weighEveryIndicator();

	private static final WeightProfile PROFILE = ImmutableWeightProfile.builder()
			.dimensionWeights(dimensionWeights())
			.build();

	public static WeightProfile profile() {
		return PROFILE;
	}

	/**
	 * The weight of each indicator within the single score of its own dimension, the same for every user: a compass
	 * attempt moves the dimension weights only (ADR 0005).
	 */
	static Map<SustainabilityIndicator, Double> indicatorWeights() {
		return INDICATOR_WEIGHTS;
	}

	private static Map<SustainabilityIndicator, Double> weighEveryIndicator() {
		var weights = new EnumMap<SustainabilityIndicator, Double>(SustainabilityIndicator.class);

		weights.put(CLIMATE_CHANGE, 0.2106);
		weights.put(OZONE_DEPLETION, 0.0631);
		weights.put(IONIZING_RADIATION, 0.0501);
		weights.put(OZONE_FORMATION, 0.0478);
		weights.put(PARTICULATE_MATTER, 0.0896);
		weights.put(NON_CARCINOGENIC_TOXICITY, 0.0184);
		weights.put(CARCINOGENIC_TOXICITY, 0.0213);
		weights.put(LAND_WATER_ACIDIFICATION, 0.062);
		weights.put(FRESHWATER_EUTROPHICATION, 0.028);
		weights.put(MARINE_EUTROPHICATION, 0.0296);
		weights.put(TERRESTRIAL_EUTROPHICATION, 0.0371);
		weights.put(FRESHWATER_ECOTOXICITY, 0.0192);
		weights.put(LAND_USE, 0.0794);
		weights.put(WATER_USE, 0.0851);
		weights.put(ENERGY_USE, 0.0832);
		weights.put(MINERAL_USE, 0.0755);

		weights.put(ANIMAL_WELFARE_INDEX, 0.5);
		weights.put(ANTIBIOTIC_INDEX, 0.5);

		// The fourteen socio-economic indicators are weighted evenly, at WP3's own rounding of one fourteenth.
		for (var indicator : SustainabilityIndicator.of(SOCIO_ECONOMIC)) {
			weights.put(indicator, 0.071429);
		}

		return Collections.unmodifiableMap(weights);
	}

	private static Map<ScoredSustainabilityDimension, Double> dimensionWeights() {
		var weights = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		weights.put(ENVIRONMENT, 0.25);
		weights.put(ANIMAL_WELFARE, 0.25);
		weights.put(SOCIO_ECONOMIC, 0.25);
		weights.put(HEALTH, 0.25);
		return weights;
	}

	private ScientificWeights() {
		// NOOP
	}
}
