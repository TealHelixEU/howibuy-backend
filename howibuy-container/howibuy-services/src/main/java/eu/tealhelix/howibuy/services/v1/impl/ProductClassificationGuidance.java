package eu.tealhelix.howibuy.services.v1.impl;

import java.util.Comparator;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Supplies extra, category-specific guidance to inject into the archetype-product classifier's system prompt. Some
 * branches of the SAFAD taxonomy need steering the global prompt must not carry (adding it there perturbs unrelated
 * near-tie decisions across every other category), so guidance is attached to the branch it applies to.
 *
 * <p>A {@link Rule} is keyed by a language and a category path prefix, from L1 downward; it applies to a product in
 * that language whose category path starts with that prefix (so a rule on {@code ["Meat …"]} covers every subcategory
 * under it). The language is part of the key because guidance may name vocabulary in one language that would be noise
 * in another. When several rules apply, the most specific — the longest matching prefix — wins. A product matching no
 * rule gets no extra guidance (the empty string), which leaves the system prompt byte-for-byte as it is without any rule.
 */
@ApplicationScoped
public class ProductClassificationGuidance {

	/**
	 * A language (an ISO code), a prefix of category names from L1 downward, and the guidance to apply to products in
	 * that language under that prefix.
	 */
	public record Rule(String language, List<String> categoryPathPrefix, String guidance) {}

	private static final String GREEK = "el";

	private static final String MEAT_L1 = "Meat and meat products (including edible offal)";
	private static final String FISH_L1 = "Fish and other seafood (including amphibians, reptiles, snails and insects)";

	/**
	 * The plain-cut categories: those whose archetypes are cuts of meat or fillets of seafood that default to raw. The
	 * processed L2s under the same L1 — {@code Sausages}, {@code Preserved meat}, {@code Meat specialities},
	 * {@code Pastes, pâtés and terrines}, {@code Meat imitates} and {@code Fish products} — are deliberately absent: their
	 * archetypes are already prepared, so the raw default does not apply and steering them only misleads the model.
	 */
	private static final List<List<String>> CUT_CATEGORIES = List.of(
			List.of(MEAT_L1, "Livestock meat"),
			List.of(MEAT_L1, "Poultry"),
			List.of(MEAT_L1, "Game birds"),
			List.of(MEAT_L1, "Game mammals"),
			List.of(MEAT_L1, "Edible offal, farmed animals"),
			List.of(MEAT_L1, "Edible offal, game animals"),
			List.of(MEAT_L1, "Mixed meat"),
			List.of(FISH_L1, "Fish meat"),
			List.of(FISH_L1, "Crustaceans"),
			List.of(FISH_L1, "Water molluscs"),
			List.of(FISH_L1, "Amphibians, reptiles, snails, insects"));

	/**
	 * Whether a meat or fish product is raw or prepared decides which archetype it maps to, and the model reaches for a
	 * cooked/cured variant far too readily. The leading blank line is deliberate: the slot sits directly after the last
	 * word of the shared prompt, so the guidance carries its own separation from it and from the example that follows.
	 */
	private static final String PREPARATION_STEERING = "\n\n" + String.join("\n",
			"Some archetypes differ only by how the food was prepared or cooked — for example raw versus grilled, roasted, fried,",
			"boiled, smoked or cured. Match the product's actual preparation. A plain retail cut of meat or fillet of fish defaults",
			"to the raw / unprepared variant, even when it is marinated or breaded — marinating and breading do not cook it. Choose a",
			"cooked, smoked, cured or otherwise preserved variant only when the product name or characteristics say the product",
			"itself is cooked or preserved: in Greek, words such as ψητό (roasted), βραστό (boiled), καπνιστό (smoked), μαγειρεμένο",
			"(cooked) or παστό (cured/salted), and ready-to-eat products such as γύρος (gyros), sausages, meatballs and sliced deli",
			"meats (αλλαντικά).");

	private static final List<Rule> DEFAULT_RULES = CUT_CATEGORIES.stream()
			.map(prefix -> new Rule(GREEK, prefix, PREPARATION_STEERING))
			.toList();

	private final List<Rule> rules;

	public ProductClassificationGuidance() {
		this(DEFAULT_RULES);
	}

	ProductClassificationGuidance(List<Rule> rules) {
		this.rules = rules;
	}

	/** The guidance for a {@code language} product at {@code categoryPath} (L1 first), or the empty string if no rule applies. */
	public String forCategoryPath(String language, List<String> categoryPath) {
		return rules.stream()
				.filter(rule -> rule.language().equals(language))
				.filter(rule -> isPrefix(rule.categoryPathPrefix(), categoryPath))
				.max(Comparator.comparingInt(rule -> rule.categoryPathPrefix().size()))
				.map(Rule::guidance)
				.orElse("");
	}

	private static boolean isPrefix(List<String> prefix, List<String> path) {
		return prefix.size() <= path.size() && path.subList(0, prefix.size()).equals(prefix);
	}
}
