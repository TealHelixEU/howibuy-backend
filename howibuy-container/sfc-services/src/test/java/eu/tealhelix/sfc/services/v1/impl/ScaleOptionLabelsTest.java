package eu.tealhelix.sfc.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;

import eu.tealhelix.sfc.v1.types.ScaleOption;
import org.junit.jupiter.api.Test;

/**
 * The five Likert scale labels are chrome, served from a resource bundle keyed by the {@link ScaleOption} enum rather
 * than from the database (ADR 0002). Every configured language resolves to a full set of five labels; a language with no
 * bundle of its own falls back to English rather than to the server's own locale.
 */
public class ScaleOptionLabelsTest {
	private final ScaleOptionLabels labels = new ScaleOptionLabels();

	@Test
	void resolvesTheEnglishLabels() {
		assertEquals(Map.of(
				ScaleOption.NOT_IMPORTANT, "Not important",
				ScaleOption.SLIGHTLY_IMPORTANT, "Slightly important",
				ScaleOption.MODERATELY_IMPORTANT, "Moderately important",
				ScaleOption.VERY_IMPORTANT, "Very important",
				ScaleOption.EXTREMELY_IMPORTANT, "Extremely important"),
				labels.forLanguage("en"));
	}

	@Test
	void resolvesTheGreekLabels() {
		assertEquals(Map.of(
				ScaleOption.NOT_IMPORTANT, "Καθόλου σημαντικό",
				ScaleOption.SLIGHTLY_IMPORTANT, "Ελάχιστα σημαντικό",
				ScaleOption.MODERATELY_IMPORTANT, "Μέτρια σημαντικό",
				ScaleOption.VERY_IMPORTANT, "Πολύ σημαντικό",
				ScaleOption.EXTREMELY_IMPORTANT, "Εξαιρετικά σημαντικό"),
				labels.forLanguage("el"));
	}

	@Test
	void everyConfiguredLanguageHasAFullLocalizedSet() {
		var english = labels.forLanguage("en");
		for (var language : new String[] {"el", "nl", "et", "de"}) {
			var localized = labels.forLanguage(language);
			assertEquals(ScaleOption.values().length, localized.size(), () -> "all five labels present in " + language);
			localized.values().forEach(label -> assertFalse(label.isBlank(), () -> "no blank label in " + language));
			assertNotEquals(english, localized, () -> "labels are localized, not the English fallback, in " + language);
		}
	}
}
