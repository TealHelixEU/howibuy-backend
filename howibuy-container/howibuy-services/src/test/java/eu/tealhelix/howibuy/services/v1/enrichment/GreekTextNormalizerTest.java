package eu.tealhelix.howibuy.services.v1.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreekTextNormalizerTest {
	private final TextNormalizer sut = new GreekTextNormalizer();

	@Test
	void foldsFinalSigmaSoAllSigmaFormsMatch() {
		assertEquals("ανθοτυροσ", sut.normalize("Ανθότυρος"), "word-final ς folds to σ");
		assertEquals("ανθοτυροσ", sut.normalize("ΑΝΘΟΤΥΡΟΣ"), "uppercase final sigma folds to σ");
		assertEquals("ανθοτυροσ", sut.normalize("ανθότυροσ"), "a regular σ is already in the folded form");
	}

	@Test
	void alsoAppliesTheDefaultStrippingAndLowercasing() {
		assertEquals("βλητα", sut.normalize("Βλήτα"), "accents stripped and lower-cased like the default");
	}
}
