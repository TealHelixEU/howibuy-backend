package eu.tealhelix.howibuy.services.v1.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class DefaultTextNormalizerTest {
	private final TextNormalizer sut = new DefaultTextNormalizer();

	@Test
	void lowercasesStripsDiacriticsAndCollapsesToTokens() {
		assertEquals("extra virgin olive oil", sut.normalize("  Éxtra-Vírgin  OLIVE   Oil! "));
	}

	@Test
	void stripsBalticDiacritics() {
		assertEquals("azuolu", sut.normalize("Ąžuolų"), "Lithuanian diacritics fall out of NFD stripping, no dedicated normalizer needed");
	}

	@Test
	void doesNotFoldGreekFinalSigma() {
		assertEquals("σολομοσ", sut.normalize("σολομοσ"), "a regular sigma is left untouched");
		assertNotEquals(sut.normalize("σολομοσ"), sut.normalize("ΣΟΛΟΜΟΣ"), "the default keeps final ς distinct from σ");
	}
}
