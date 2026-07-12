package eu.tealhelix.howibuy.services.v1.enrichment;

import java.util.Map;

final class TextNormalizers {
	private static final TextNormalizer DEFAULT = new DefaultTextNormalizer();
	private static final Map<String, TextNormalizer> BY_LANGUAGE = Map.of("el", new GreekTextNormalizer());

	private TextNormalizers() {
		// This is a utility class, not to be instantiated
	}

	static TextNormalizer forLanguage(String language) {
		return BY_LANGUAGE.getOrDefault(language, DEFAULT);
	}
}
