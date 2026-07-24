package eu.tealhelix.sfc.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import eu.tealhelix.common.types.validation.BadInputValueException;
import org.junit.jupiter.api.Test;

class SfcLanguagesTest {
	private final SfcLanguages sut = new SfcLanguages(Set.of("en", "el"), "en");

	@Test
	void resolvesNullToTheDefaultLanguage() {
		assertEquals("en", sut.resolve(null));
	}

	@Test
	void resolvesBlankToTheDefaultLanguage() {
		assertEquals("en", sut.resolve("   "));
	}

	@Test
	void resolvesASupportedLanguageToItself() {
		assertEquals("el", sut.resolve("el"));
	}

	@Test
	void rejectsAnUnsupportedLanguage() {
		assertThrows(BadInputValueException.class, () -> sut.resolve("fr"));
	}
}
