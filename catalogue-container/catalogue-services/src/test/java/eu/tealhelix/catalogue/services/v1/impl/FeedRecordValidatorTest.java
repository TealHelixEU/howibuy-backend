package eu.tealhelix.catalogue.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import eu.tealhelix.catalogue.feeds.DeletionRecord;
import eu.tealhelix.catalogue.feeds.FeedRecord;
import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableCatalogue;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import org.junit.jupiter.api.Test;

class FeedRecordValidatorTest {
	private static final Catalogue CATALOGUE = ImmutableCatalogue.builder()
			.id(new CatalogueIdImpl("rimi-lt"))
			.retailerId(new GenericRetailerId("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11"))
			.country("LT")
			.primaryLanguage("lt")
			.acceptedLanguages(Set.of("lt", "en", "ru"))
			.adapter("test-adapter")
			.isActive(true)
			.build();

	private static final FeedRecord VALID = new FeedRecord(
			7,
			"P-1",
			Map.of("lt", "Pienas", "en", "Milk"),
			Map.of("fat", "2.5%"),
			List.of("dairy"),
			new BigDecimal("1.29"),
			"EUR"
	);

	@Test
	void acceptsAValidRecord() {
		assertEquals(Optional.empty(), FeedRecordValidator.findFault(VALID, CATALOGUE));
	}

	@Test
	void acceptsARecordWithoutCharacteristicsAndTags() {
		assertAccepted(new FeedRecord(7, "P-1", Map.of("lt", "Pienas"), null, null, new BigDecimal("1.29"), "EUR"));
		assertAccepted(new FeedRecord(7, "P-1", Map.of("lt", "Pienas"), Map.of(), List.of(), new BigDecimal("1.29"), "EUR"));
	}

	@Test
	void requiresAProductKey() {
		assertFault("The product key is missing", withProductKey(null));
		assertFault("The product key is missing", withProductKey(" "));
	}

	@Test
	void limitsTheLengthOfTheProductKey() {
		assertAccepted(withProductKey("k".repeat(255)));
		assertFault("The product key is longer than 255 characters", withProductKey("k".repeat(256)));
	}

	@Test
	void requiresANameInThePrimaryLanguage() {
		assertFault("There is no name in the primary language of the Catalogue, language: lt", withNames(null));
		assertFault("There is no name in the primary language of the Catalogue, language: lt", withNames(Map.of()));
		assertFault("There is no name in the primary language of the Catalogue, language: lt", withNames(Map.of("en", "Milk")));
	}

	@Test
	void requiresTheLanguagesToBeAccepted() {
		assertFault("The Catalogue does not accept the language, language: de", withNames(Map.of("lt", "Pienas", "de", "Milch")));
	}

	@Test
	void requiresTheNamesNotToBeBlank() {
		assertFault("A name is blank, language: en", withNames(Map.of("lt", "Pienas", "en", " ")));
		var names = new HashMap<String, String>();
		names.put("lt", "Pienas");
		names.put("en", null);
		assertFault("A name is blank, language: en", withNames(names));
	}

	@Test
	void limitsTheLengthOfTheNames() {
		assertAccepted(withNames(Map.of("lt", "n".repeat(512))));
		assertFault("A name is longer than 512 characters, language: lt", withNames(Map.of("lt", "n".repeat(513))));
	}

	@Test
	void requiresAPositivePrice() {
		assertFault("The price is missing", withPrice(null));
		assertFault("The price is not positive, price: 0", withPrice(BigDecimal.ZERO));
		assertFault("The price is not positive, price: -1.29", withPrice(new BigDecimal("-1.29")));
	}

	@Test
	void requiresThePriceToFitTheStoredPrecision() {
		assertAccepted(withPrice(new BigDecimal("99999999.9999")));
		assertAccepted(withPrice(new BigDecimal("1.290000")));
		assertFault("The price has more than 8 integer or 4 decimal digits, price: 100000000", withPrice(new BigDecimal("100000000")));
		assertFault("The price has more than 8 integer or 4 decimal digits, price: 1.29001", withPrice(new BigDecimal("1.29001")));
	}

	@Test
	void requiresAKnownCurrency() {
		assertFault("The currency is missing", withCurrency(null));
		assertFault("The currency is missing", withCurrency(""));
		assertFault("The currency is not an ISO 4217 code, currency: EUX", withCurrency("EUX"));
		assertFault("The currency is not an ISO 4217 code, currency: eur", withCurrency("eur"));
	}

	@Test
	void requiresTheCharacteristicsToBeNamedAndValued() {
		assertFault("A characteristic has no name", withCharacteristics(Map.of(" ", "2.5%")));
		var characteristics = new HashMap<String, String>();
		characteristics.put("fat", null);
		assertFault("A characteristic has no value, characteristic: fat", withCharacteristics(characteristics));
	}

	@Test
	void requiresTheTagsNotToBeBlank() {
		assertFault("A tag is blank", withTags(List.of("dairy", " ")));
		var tags = new ArrayList<String>();
		tags.add(null);
		assertFault("A tag is blank", withTags(tags));
	}

	@Test
	void holdsADeletionToTheRulesOfTheProductKey() {
		assertEquals(Optional.empty(), FeedRecordValidator.findFault(new DeletionRecord(3, "k".repeat(255))));
		assertEquals(Optional.of("The product key is missing"), FeedRecordValidator.findFault(new DeletionRecord(3, null)));
		assertEquals(Optional.of("The product key is missing"), FeedRecordValidator.findFault(new DeletionRecord(3, " ")));
		assertEquals(Optional.of("The product key is longer than 255 characters"), FeedRecordValidator.findFault(new DeletionRecord(3, "k".repeat(256))));
	}

	private static void assertAccepted(FeedRecord record) {
		assertEquals(Optional.empty(), FeedRecordValidator.findFault(record, CATALOGUE));
	}

	private static void assertFault(String fault, FeedRecord record) {
		assertEquals(Optional.of(fault), FeedRecordValidator.findFault(record, CATALOGUE));
	}

	private static FeedRecord withProductKey(String productKey) {
		return new FeedRecord(VALID.position(), productKey, VALID.names(), VALID.characteristics(), VALID.tags(), VALID.price(), VALID.currency());
	}

	private static FeedRecord withNames(Map<String, String> names) {
		return new FeedRecord(VALID.position(), VALID.productKey(), names, VALID.characteristics(), VALID.tags(), VALID.price(), VALID.currency());
	}

	private static FeedRecord withCharacteristics(Map<String, String> characteristics) {
		return new FeedRecord(VALID.position(), VALID.productKey(), VALID.names(), characteristics, VALID.tags(), VALID.price(), VALID.currency());
	}

	private static FeedRecord withTags(List<String> tags) {
		return new FeedRecord(VALID.position(), VALID.productKey(), VALID.names(), VALID.characteristics(), tags, VALID.price(), VALID.currency());
	}

	private static FeedRecord withPrice(BigDecimal price) {
		return new FeedRecord(VALID.position(), VALID.productKey(), VALID.names(), VALID.characteristics(), VALID.tags(), price, VALID.currency());
	}

	private static FeedRecord withCurrency(String currency) {
		return new FeedRecord(VALID.position(), VALID.productKey(), VALID.names(), VALID.characteristics(), VALID.tags(), VALID.price(), currency);
	}
}
