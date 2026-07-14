package eu.tealhelix.howibuy.services.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.immutables.value.Value;

/**
 * A generic food term carried between the DAO and service layers to enrich a retailer product name before AI
 * classification. The {@link #getTerm() term} is the native-language name to match against; the
 * {@link #getCanonicalEn() canonical English name}, {@link #getDescription() description} and optional
 * {@link #getCategoryHint() category hint} are the world knowledge injected into the classifier's input.
 */
@Value.Immutable
public interface FoodTerm {
	String getTerm();

	String getCanonicalEn();

	String getDescription();

	/**
	 * An optional SAFAD taxonomy path locating this term in the category hierarchy, from L1 downward, node names
	 * separated by {@code " → "} (U+2192 RIGHTWARDS ARROW), e.g. {@code "Milk and dairy products → Cheese"}. Written as
	 * deep as is known — it may stop at any level — and absent when the category is unknown. Injected as guidance into
	 * the classification descent.
	 */
	Optional<String> getCategoryHint();

	/**
	 * The {@link #getCategoryHint() category hint} split into its node names, top-level first, or an empty list when no
	 * hint is set. E.g. {@code "Milk and dairy products → Cheese"} yields {@code ["Milk and dairy products", "Cheese"]}.
	 */
	default List<String> getCategoryHintPath() {
		return getCategoryHint()
				.map(hint -> Arrays.stream(hint.split("→"))
						.map(String::trim)
						.filter(node -> !node.isEmpty())
						.toList())
				.orElseGet(List::of);
	}
}
