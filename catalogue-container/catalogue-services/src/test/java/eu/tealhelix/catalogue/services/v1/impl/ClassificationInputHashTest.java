package eu.tealhelix.catalogue.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ClassificationInputHashTest {
	private static final Map<String, String> NAMES = Map.of("lt", "Pienas", "en", "Milk");
	private static final Map<String, String> CHARACTERISTICS = Map.of("fat", "2.5%", "volume", "1l");
	private static final List<String> TAGS = List.of("dairy", "organic");

	@Test
	void isTheLowercaseHexOfSha256() {
		assertTrue(ClassificationInputHash.of(NAMES, CHARACTERISTICS, TAGS).matches("[0-9a-f]{64}"));
	}

	@Test
	void doesNotDependOnTheOrderOfTheMapsAndTheTags() {
		var names = new LinkedHashMap<String, String>();
		names.put("en", "Milk");
		names.put("lt", "Pienas");
		var otherNames = new LinkedHashMap<String, String>();
		otherNames.put("lt", "Pienas");
		otherNames.put("en", "Milk");
		var characteristics = new LinkedHashMap<String, String>();
		characteristics.put("fat", "2.5%");
		characteristics.put("volume", "1l");
		var otherCharacteristics = new LinkedHashMap<String, String>();
		otherCharacteristics.put("volume", "1l");
		otherCharacteristics.put("fat", "2.5%");

		assertEquals(
				ClassificationInputHash.of(otherNames, otherCharacteristics, List.of("organic", "dairy")),
				ClassificationInputHash.of(names, characteristics, List.of("dairy", "organic"))
		);
	}

	@Test
	void ignoresSurroundingWhitespace() {
		assertEquals(
				ClassificationInputHash.of(Map.of("en", "Milk"), Map.of("fat", "2.5%"), List.of("dairy")),
				ClassificationInputHash.of(Map.of(" en ", " Milk\t"), Map.of(" fat", "2.5% "), List.of(" dairy "))
		);
	}

	@Test
	void doesNotDependOnTheUnicodeNormalisationForm() {
		var composed = "Caf\u00e9";
		var decomposed = "Cafe\u0301";

		assertEquals(
				ClassificationInputHash.of(Map.of("en", composed), Map.of(), List.of(composed)),
				ClassificationInputHash.of(Map.of("en", decomposed), Map.of(), List.of(decomposed))
		);
	}

	@Test
	void ignoresRepeatedTags() {
		assertEquals(
				ClassificationInputHash.of(NAMES, CHARACTERISTICS, TAGS),
				ClassificationInputHash.of(NAMES, CHARACTERISTICS, List.of("dairy", "organic", "dairy"))
		);
	}

	@Test
	void keepsTheCase() {
		assertNotEquals(
				ClassificationInputHash.of(Map.of("en", "Milk"), CHARACTERISTICS, TAGS),
				ClassificationInputHash.of(Map.of("en", "MILK"), CHARACTERISTICS, TAGS)
		);
	}

	@Test
	void changesWithEachName() {
		var hash = ClassificationInputHash.of(NAMES, CHARACTERISTICS, TAGS);

		assertNotEquals(hash, ClassificationInputHash.of(Map.of("lt", "Pienas", "en", "Whole milk"), CHARACTERISTICS, TAGS));
		assertNotEquals(hash, ClassificationInputHash.of(Map.of("lt", "Pienas"), CHARACTERISTICS, TAGS));
		assertNotEquals(hash, ClassificationInputHash.of(Map.of("lt", "Pienas", "lv", "Milk"), CHARACTERISTICS, TAGS));
	}

	@Test
	void changesWithEachCharacteristicAndTag() {
		var hash = ClassificationInputHash.of(NAMES, CHARACTERISTICS, TAGS);

		assertNotEquals(hash, ClassificationInputHash.of(NAMES, Map.of("fat", "3.5%", "volume", "1l"), TAGS));
		assertNotEquals(hash, ClassificationInputHash.of(NAMES, Map.of("fat", "2.5%"), TAGS));
		assertNotEquals(hash, ClassificationInputHash.of(NAMES, CHARACTERISTICS, List.of("dairy")));
	}

	@Test
	void keepsTheValuesApart() {
		assertNotEquals(
				ClassificationInputHash.of(Map.of(), Map.of(), List.of("a", "bc")),
				ClassificationInputHash.of(Map.of(), Map.of(), List.of("ab", "c"))
		);
		assertNotEquals(
				ClassificationInputHash.of(Map.of(), Map.of("ab", ""), List.of()),
				ClassificationInputHash.of(Map.of(), Map.of("a", "b"), List.of())
		);
	}

	@Test
	void keepsTheNamesTheCharacteristicsAndTheTagsApart() {
		assertNotEquals(
				ClassificationInputHash.of(Map.of(), Map.of("en", "Milk"), List.of()),
				ClassificationInputHash.of(Map.of("en", "Milk"), Map.of(), List.of())
		);
		assertNotEquals(
				ClassificationInputHash.of(Map.of("Milk", ""), Map.of(), List.of()),
				ClassificationInputHash.of(Map.of(), Map.of(), List.of("Milk"))
		);
	}
}
