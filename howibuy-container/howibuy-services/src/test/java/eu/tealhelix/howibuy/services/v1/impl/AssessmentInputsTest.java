package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.services.v1.ProductAssessmentService.MAX_PRODUCTS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class AssessmentInputsTest {
	private static final String LANGUAGE = "en";
	private static final ProductKey PRODUCT = new ProductKeyImpl("product-key");

	@ParameterizedTest
	@NullAndEmptySource
	void refusesAMissingLanguage(String language) {
		var e = assertThrows(RequiredInputMissingException.class, () -> AssessmentInputs.requireLanguageAndProductKeys(language, List.of(PRODUCT)));

		assertEquals("language", e.getInputName());
	}

	@Test
	void refusesAMissingListOfProductKeys() {
		var e = assertThrows(RequiredInputMissingException.class, () -> AssessmentInputs.requireLanguageAndProductKeys(LANGUAGE, null));

		assertEquals("productKeys", e.getInputName());
	}

	@Test
	void refusesAMissingProductKey() {
		var e = assertThrows(RequiredInputMissingException.class, () -> AssessmentInputs.requireLanguageAndProductKeys(LANGUAGE, Arrays.asList(PRODUCT, null)));

		assertEquals("productKeys", e.getInputName());
	}

	@Test
	void refusesMoreProductsThanTheMost() {
		var tooMany = Collections.nCopies(MAX_PRODUCTS + 1, PRODUCT);

		var e = assertThrows(BadInputValueException.class, () -> AssessmentInputs.requireLanguageAndProductKeys(LANGUAGE, tooMany));

		assertEquals("productKeys", e.getInputName());
		assertEquals("There must be at most " + MAX_PRODUCTS, e.getHint());
	}

	@Test
	void acceptsFromNoProductToTheMost() {
		assertDoesNotThrow(() -> AssessmentInputs.requireLanguageAndProductKeys(LANGUAGE, List.of()));
		assertDoesNotThrow(() -> AssessmentInputs.requireLanguageAndProductKeys(LANGUAGE, Collections.nCopies(MAX_PRODUCTS, PRODUCT)));
	}
}
