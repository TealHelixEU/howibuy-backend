package eu.tealhelix.howibuy.v1.types;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ANIMAL_WELFARE;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;
import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.SOCIAL;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One measured quantity of an archetype product, contributing to the single score of exactly one scored sustainability
 * dimension. The environmental indicators are the sixteen PEF impact categories; the social indicators come from the
 * Social Hotspots Database; animal welfare has two indices.
 * <p>
 * {@link ScoredSustainabilityDimension#HEALTH} appears nowhere here: it is read off the product's Nutri-Score
 * rather than measured, so it has no indicators.
 */
public enum SustainabilityIndicator {
	CLIMATE_CHANGE(ENVIRONMENT),
	OZONE_DEPLETION(ENVIRONMENT),
	IONIZING_RADIATION(ENVIRONMENT),
	OZONE_FORMATION(ENVIRONMENT),
	PARTICULATE_MATTER(ENVIRONMENT),
	NON_CARCINOGENIC_TOXICITY(ENVIRONMENT),
	CARCINOGENIC_TOXICITY(ENVIRONMENT),
	LAND_WATER_ACIDIFICATION(ENVIRONMENT),
	FRESHWATER_EUTROPHICATION(ENVIRONMENT),
	MARINE_EUTROPHICATION(ENVIRONMENT),
	TERRESTRIAL_EUTROPHICATION(ENVIRONMENT),
	FRESHWATER_ECOTOXICITY(ENVIRONMENT),
	LAND_USE(ENVIRONMENT),
	WATER_USE(ENVIRONMENT),
	ENERGY_USE(ENVIRONMENT),
	MINERAL_USE(ENVIRONMENT),

	ANIMAL_WELFARE_INDEX(ANIMAL_WELFARE),
	ANTIBIOTIC_INDEX(ANIMAL_WELFARE),

	CHILD_LABOUR(SOCIAL),
	FORCED_LABOUR(SOCIAL),
	FAIR_SALARY(SOCIAL),
	WORKING_TIME(SOCIAL),
	DISCRIMINATION(SOCIAL),
	HEALTH_SAFETY_WORKERS(SOCIAL),
	SOCIAL_BENEFITS_LEGAL_ISSUES(SOCIAL),
	WORKERS_RIGHTS(SOCIAL),
	FAIR_COMPETITION(SOCIAL),
	CORRUPTION(SOCIAL),
	CONTRIBUTION_ECON_DEV(SOCIAL),
	ILLITERACY(SOCIAL),
	HEALTH_SAFETY_SOCIETY(SOCIAL),
	INDIGENOUS_RIGHTS(SOCIAL);

	private final ScoredSustainabilityDimension dimension;

	SustainabilityIndicator(ScoredSustainabilityDimension dimension) {
		this.dimension = dimension;
	}

	public ScoredSustainabilityDimension getDimension() {
		return dimension;
	}

	/**
	 * The indicators contributing to the given dimension, in declaration order. Empty for
	 * {@link ScoredSustainabilityDimension#HEALTH}.
	 */
	public static List<SustainabilityIndicator> of(ScoredSustainabilityDimension dimension) {
		return BY_DIMENSION.getOrDefault(dimension, List.of());
	}

	private static final Map<ScoredSustainabilityDimension, List<SustainabilityIndicator>> BY_DIMENSION =
			Arrays.stream(values()).collect(
					Collectors.groupingBy(
							SustainabilityIndicator::getDimension,
							() -> new EnumMap<>(ScoredSustainabilityDimension.class),
							Collectors.toUnmodifiableList()
					)
			);
}
