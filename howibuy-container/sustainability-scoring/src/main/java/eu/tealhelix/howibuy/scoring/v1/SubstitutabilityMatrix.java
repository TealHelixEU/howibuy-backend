package eu.tealhelix.howibuy.scoring.v1;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Which L2 categories may stand in for which, and how readily.
 * <p>
 * Indexed by the category being substituted <em>for</em>, because that is the question the search asks: given the
 * category of the product the user scanned, what else would do. The matrix WP3 delivered is symmetric, so the
 * direction currently makes no difference to the answer — the index follows the question anyway, so that an
 * asymmetric revision cannot quietly invert it.
 */
public final class SubstitutabilityMatrix {
	private final Map<UUID, Map<UUID, Short>> substitutesByCategory;

	public static SubstitutabilityMatrix of(Collection<SubstitutablePair> pairs) {
		var substitutesByCategory = new HashMap<UUID, Map<UUID, Short>>();
		for (var pair : pairs) {
			substitutesByCategory
					.computeIfAbsent(pair.toCategoryId(), _ -> new HashMap<>())
					.put(pair.fromCategoryId(), pair.degree());
		}
		return new SubstitutabilityMatrix(substitutesByCategory);
	}

	/**
	 * The categories that may substitute for the given one at or above the given degree. A category is normally
	 * substitutable for itself, so the answer usually contains the category asked about; it is empty for a category
	 * the matrix does not mention at all.
	 */
	public Set<UUID> categoriesSubstitutableFor(UUID categoryId, short minimumDegree) {
		var substitutes = substitutesByCategory.get(categoryId);
		if (substitutes == null) return Set.of();
		return substitutes.entrySet().stream()
				.filter(substitute -> substitute.getValue() >= minimumDegree)
				.map(Map.Entry::getKey)
				.collect(Collectors.toSet());
	}

	private SubstitutabilityMatrix(Map<UUID, Map<UUID, Short>> substitutesByCategory) {
		this.substitutesByCategory = substitutesByCategory;
	}
}
