package eu.tealhelix.howibuy.scoring.v1;

import java.util.EnumMap;
import java.util.Map;

import eu.tealhelix.howibuy.v1.types.SubstitutabilityLevel;

/**
 * How far the search may stray from the scanned product, and how it weighs the two rankings against each other.
 *
 * @param level            how distant a substitute may be
 * @param minimumDegrees   the matrix degree a pair must reach to qualify, per level
 * @param personalWeight   the weight of the personal overall score in the combined ranking
 * @param scientificWeight the weight of the scientific overall score in the combined ranking
 */
public record SubstitutionSettings(
		SubstitutabilityLevel level,
		Map<SubstitutabilityLevel, Short> minimumDegrees,
		double personalWeight,
		double scientificWeight) {

	/**
	 * The degree cut-offs are <strong>provisional</strong>. The reference script matches the matrix against the
	 * strings {@code x}/{@code m}/{@code v}, while the encoding WP3 delivered is a 0–5 scale, and cross-tabulating the
	 * two over all 15,376 cells shows a re-rating rather than a recode — no cut-off reproduces the old buckets, so the
	 * mapping cannot be recovered from the data. These three numbers are the one part of the method we know may be
	 * wrong; they are pending question 1.1 to KU Leuven and correcting them is a configuration change.
	 * <p>
	 * The ranking weights are {@code combined_score} in {@code TH_Algorithm_Implementation_v2026-05-20.Rmd}.
	 */
	public static SubstitutionSettings defaults() {
		var minimumDegrees = new EnumMap<SubstitutabilityLevel, Short>(SubstitutabilityLevel.class);
		minimumDegrees.put(SubstitutabilityLevel.SMALL, (short) 4);
		minimumDegrees.put(SubstitutabilityLevel.MEDIUM, (short) 3);
		minimumDegrees.put(SubstitutabilityLevel.LARGE, (short) 1);
		return new SubstitutionSettings(SubstitutabilityLevel.SMALL, minimumDegrees, 0.6, 0.4);
	}

	public SubstitutionSettings {
		var missing = new EnumMap<SubstitutabilityLevel, Short>(SubstitutabilityLevel.class);
		for (var candidate : SubstitutabilityLevel.values()) {
			if (!minimumDegrees.containsKey(candidate)) missing.put(candidate, null);
		}
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException("Substitution settings are missing a minimum degree for levels: " + missing.keySet());
		}
		minimumDegrees = Map.copyOf(minimumDegrees);
	}

	/**
	 * The same settings at a different level, for a caller that has one configured.
	 */
	public SubstitutionSettings at(SubstitutabilityLevel level) {
		return new SubstitutionSettings(level, minimumDegrees, personalWeight, scientificWeight);
	}

	/**
	 * The two overall scores of one product blended into the score its combined ranking uses.
	 */
	public double combinedScore(double personalScore, double scientificScore) {
		return personalWeight * personalScore + scientificWeight * scientificScore;
	}

	public short minimumDegreeFor(SubstitutabilityLevel level) {
		return minimumDegrees.get(level);
	}

	public short minimumDegree() {
		return minimumDegreeFor(level);
	}
}
