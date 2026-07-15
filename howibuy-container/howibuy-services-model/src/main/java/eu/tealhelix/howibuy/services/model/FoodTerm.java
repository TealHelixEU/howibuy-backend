package eu.tealhelix.howibuy.services.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.immutables.value.Value;

/**
 * A generic food term carried between the DAO and service layers to enrich a retailer product name before AI
 * classification. The {@link #getTerm() term} is the native-language name to match against; the
 * {@link #getCanonicalEn() canonical English name}, {@link #getDescription() description} and the optional
 * {@link #getCategoryHintPath() category-hint path} are the world knowledge injected into the classifier's input.
 */
@Value.Immutable
public interface FoodTerm {
	String getTerm();

	String getCanonicalEn();

	String getDescription();

	/**
	 * The L1 (top-level) node of the SAFAD taxonomy path locating this term, e.g. {@code "Milk and dairy products"};
	 * absent when the category is unknown.
	 */
	Optional<String> getCategoryHintL1();

	/**
	 * The L2 node of the SAFAD taxonomy path, e.g. {@code "Cheese"}; absent when the term is not located that deep.
	 * Set only when {@link #getCategoryHintL1() L1} is set.
	 */
	Optional<String> getCategoryHintL2();

	/**
	 * The L3 node of the SAFAD taxonomy path; absent when the term is not located that deep. Set only when
	 * {@link #getCategoryHintL2() L2} is set.
	 */
	Optional<String> getCategoryHintL3();

	/**
	 * The category-hint levels as a path, top-level first — the contiguous run of set levels from L1 downward, or an
	 * empty list when no hint is set. E.g. L1 {@code "Milk and dairy products"} + L2 {@code "Cheese"} yields
	 * {@code ["Milk and dairy products", "Cheese"]}. Used to steer the taxonomy descent level by level.
	 */
	default List<String> getCategoryHintPath() {
		List<String> path = new ArrayList<>();
		for (Optional<String> level : List.of(getCategoryHintL1(), getCategoryHintL2(), getCategoryHintL3())) {
			if (level.isEmpty()) {
				break;
			}
			path.add(level.get());
		}
		return List.copyOf(path);
	}

	/**
	 * The {@link #getCategoryHintPath() category-hint path} rendered as a single string with node names separated by
	 * {@code " → "} (U+2192 RIGHTWARDS ARROW), e.g. {@code "Milk and dairy products → Cheese"}, or absent when no hint
	 * is set. Injected as guidance into the classifier's input.
	 */
	default Optional<String> getCategoryHint() {
		List<String> path = getCategoryHintPath();
		return path.isEmpty() ? Optional.empty() : Optional.of(String.join(" → ", path));
	}
}
