package eu.tealhelix.howibuy.scoring.v1;

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

import java.util.EnumMap;
import java.util.Map;

import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;

/**
 * The Product Environmental Footprint normalization factors: for each impact category, the impact one average person
 * causes in a year, in that category's own unit. Dividing by them is what makes sixteen quantities measured in
 * kilograms, cubic metres and megajoules addable into one number.
 * <p>
 * Transcribed from {@code E_normalization_factors} in {@code TH_Algorithm_Implementation_v2026-05-20.Rmd}.
 */
final class PefNormalization {
	/** From {@code E_factors_* = 1000 * E_*_weights / E_normalization_factors} in the same script. */
	private static final double PER_THOUSAND_PERSONS = 1000.0;

	private static final Map<SustainabilityIndicator, Double> FACTORS = factors();

	/**
	 * The multiplier a weight is scaled by before it meets a raw indicator value. Indicators outside the environmental
	 * dimension are not PEF-normalised and scale by 1.
	 */
	static double weightScaleFor(SustainabilityIndicator indicator) {
		var factor = FACTORS.get(indicator);
		if (factor == null) return 1.0;
		else return PER_THOUSAND_PERSONS / factor;
	}

	private static Map<SustainabilityIndicator, Double> factors() {
		var factors = new EnumMap<SustainabilityIndicator, Double>(SustainabilityIndicator.class);
		factors.put(CLIMATE_CHANGE, 7553.08316285117);
		factors.put(OZONE_DEPLETION, 0.0523483833840181);
		factors.put(IONIZING_RADIATION, 4220.16339014993);
		factors.put(OZONE_FORMATION, 40.8591977347772);
		factors.put(PARTICULATE_MATTER, 0.000595366821125478);
		factors.put(NON_CARCINOGENIC_TOXICITY, 0.000128735735008072);
		factors.put(CARCINOGENIC_TOXICITY, 1.72528976538705E-05);
		factors.put(LAND_WATER_ACIDIFICATION, 55.5695412306019);
		factors.put(FRESHWATER_EUTROPHICATION, 1.60685212828813);
		factors.put(MARINE_EUTROPHICATION, 19.5451815519191);
		factors.put(TERRESTRIAL_EUTROPHICATION, 176.754999788942);
		factors.put(FRESHWATER_ECOTOXICITY, 56716.5863370596);
		factors.put(LAND_USE, 819498.182923031);
		factors.put(WATER_USE, 11468.7086407597);
		factors.put(ENERGY_USE, 65004.2596640165);
		factors.put(MINERAL_USE, 0.0636226152369547);
		return factors;
	}

	private PefNormalization() {
		// NOOP
	}
}
