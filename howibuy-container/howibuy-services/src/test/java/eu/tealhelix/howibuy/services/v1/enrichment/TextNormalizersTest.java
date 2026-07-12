package eu.tealhelix.howibuy.services.v1.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class TextNormalizersTest {

	@Test
	void greekIsSelectedForEl() {
		var greek = TextNormalizers.forLanguage("el");
		assertEquals(greek.normalize("ΑΝΘΟΤΥΡΟΣ"), greek.normalize("ανθοτυροσ"), "the el normalizer folds final sigma");
	}

	@Test
	void unknownLanguageFallsBackToTheDefault() {
		var other = TextNormalizers.forLanguage("lt");
		assertNotEquals(other.normalize("ΑΝΘΟΤΥΡΟΣ"), other.normalize("ανθοτυροσ"), "a language without a dedicated normalizer does not fold final sigma");
	}
}
