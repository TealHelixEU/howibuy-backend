package eu.tealhelix.howibuy.services.v1.ai.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class for rendering the model structures to strings that will be sent to the AI.
 */
class RenderingHelper {
	public static final String CHARACTERISTICS_HEADER = "### Product Characteristics\n\n";
	public static final String TAGS_HEADER = "### Product Tags\n\n";

	private RenderingHelper() {
		// This is a utility class, not to be instantiated
	}

	public static String renderTheProductCharacteristics(Map<String, String> characteristics) {
		if (characteristics == null || characteristics.isEmpty()) return "";
		return characteristics.entrySet().stream()
				.map(e -> "- " + e.getKey() + ": " + e.getValue())
				.collect(Collectors.joining("\n", CHARACTERISTICS_HEADER, "\n"));
	}

	public static String renderTheProductTags(List<String> tags) {
		if (tags == null || tags.isEmpty()) return "";
		return tags.stream()
				.map(t -> "- " + t)
				.collect(Collectors.joining("\n", TAGS_HEADER, "\n"));
	}

	public static String renderCategories(List<String> categories) {
		if (categories == null || categories.isEmpty()) {
			throw new IllegalArgumentException("categories must not be null or empty");
		}
		return categories.stream()
				.map(t -> "- " + t)
				.collect(Collectors.joining("\n"));
	}
}
