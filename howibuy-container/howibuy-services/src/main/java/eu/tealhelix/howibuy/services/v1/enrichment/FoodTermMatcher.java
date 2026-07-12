package eu.tealhelix.howibuy.services.v1.enrichment;

import java.util.Comparator;
import java.util.List;

import eu.tealhelix.howibuy.services.model.FoodTerm;

/**
 * Finds the glossary terms that occur in a product name. Matching is language-agnostic and token-exact: the given
 * {@link TextNormalizer} reduces both sides to comparable tokens, and a term matches when its token sequence appears
 * contiguously among the name's tokens. This deliberately does not chase inflected forms — precision over recall for
 * the direct-scan stage.
 */
final class FoodTermMatcher {
	private FoodTermMatcher() {
		// This is a utility class, not to be instantiated
	}

	static List<FoodTerm> match(List<FoodTerm> glossary, String productName, TextNormalizer normalizer) {
		var nameTokens = tokenize(productName, normalizer);
		if (nameTokens.isEmpty()) {
			return List.of();
		}
		return glossary.stream()
				.filter(term -> occursIn(nameTokens, term, normalizer))
				.sorted(mostSpecificFirst(normalizer))
				.toList();
	}

	private static boolean occursIn(List<String> nameTokens, FoodTerm term, TextNormalizer normalizer) {
		var termTokens = tokenize(term.getTerm(), normalizer);
		return !termTokens.isEmpty() && containsContiguously(nameTokens, termTokens);
	}

	private static Comparator<FoodTerm> mostSpecificFirst(TextNormalizer normalizer) {
		return Comparator.comparingInt((FoodTerm term) -> tokenize(term.getTerm(), normalizer).size()).reversed()
				.thenComparing(FoodTerm::getTerm);
	}

	private static boolean containsContiguously(List<String> haystack, List<String> needle) {
		for (int start = 0; start <= haystack.size() - needle.size(); start++) {
			if (haystack.subList(start, start + needle.size()).equals(needle)) {
				return true;
			}
		}
		return false;
	}

	private static List<String> tokenize(String text, TextNormalizer normalizer) {
		var normalized = normalizer.normalize(text);
		return normalized.isEmpty() ? List.of() : List.of(normalized.split(" "));
	}
}
