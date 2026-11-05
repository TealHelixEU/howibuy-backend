package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension.ENVIRONMENT;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import eu.tealhelix.howibuy.services.model.ArchetypeProductImpacts;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeProductImpacts;
import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.ImmutableWeightProfile;
import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeCategoryIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;

/**
 * Archetype products whose scores can be computed by hand.
 * <p>
 * Each product's indicators are set to a per-dimension fraction of one tiny base value — tiny so that no single score
 * comes near an outlier threshold and the plain min–max applies, and a fraction because min–max is scale-free: a
 * product at fraction {@code f} of a dimension whose corpus spans 0 to 1 normalises to exactly {@code 1 - f}. The
 * health score comes straight off the Nutri-Score.
 */
final class ArchetypeImpactsFixtures {
	private static final double BASE_VALUE = 1e-9;

	/**
	 * A branch of the SAFAD taxonomy: the L2 node substitutability is decided at, plus the whole path a recommendation
	 * reports. The id is derived from the L2 name, so a failure names a category a reader can find in the test.
	 */
	record Branch(String l1, String l2, String l3) {
		ArchetypeCategoryId id() {
			return categoryId(l2);
		}
	}

	/**
	 * A user who cares about nothing but the environment, so that the personal overall score is one of the four
	 * normalised scores rather than a blend of them.
	 */
	static WeightProfile personalProfileOnEnvironment() {
		var dimensionWeights = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		for (var dimension : ScoredSustainabilityDimension.values()) {
			dimensionWeights.put(dimension, dimension == ENVIRONMENT ? 1.0 : 0.0);
		}
		return ImmutableWeightProfile.builder()
				.dimensionWeights(dimensionWeights)
				.build();
	}

	/**
	 * Ids derived from a name, so that a failure names a product a reader can find in the test rather than an opaque
	 * UUID.
	 */
	static ArchetypeProductId productId(String name) {
		return new ArchetypeProductIdImpl(UUID.nameUUIDFromBytes(name.getBytes()).toString());
	}

	private static ArchetypeCategoryId categoryId(String name) {
		return new ArchetypeCategoryIdImpl(UUID.nameUUIDFromBytes(name.getBytes()).toString());
	}

	static ArchetypeProductImpacts impacts(
			ArchetypeProductId id, String name, String agbCode, Branch branch,
			double environment, double animalWelfare, double socioEconomic, String nutriScore) {
		var values = new EnumMap<SustainabilityIndicator, Double>(SustainabilityIndicator.class);
		for (var indicator : SustainabilityIndicator.values()) {
			values.put(indicator, BASE_VALUE * switch (indicator.getDimension()) {
				case ENVIRONMENT -> environment;
				case ANIMAL_WELFARE -> animalWelfare;
				case SOCIO_ECONOMIC -> socioEconomic;
				case HEALTH -> 0.0;
			});
		}
		return ImmutableArchetypeProductImpacts.builder()
				.id(id)
				.name(name)
				.agbCode(agbCode)
				.l2CategoryId(branch.id())
				.l1CategoryName(branch.l1())
				.l2CategoryName(branch.l2())
				.l3CategoryName(branch.l3())
				.indicatorValues(Map.copyOf(values))
				.nutriScore(nutriScore)
				.nutriScorePoints(0)
				.build();
	}

	private ArchetypeImpactsFixtures() {
	}
}
