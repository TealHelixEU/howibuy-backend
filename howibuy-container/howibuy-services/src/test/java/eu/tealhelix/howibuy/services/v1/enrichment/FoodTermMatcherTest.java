package eu.tealhelix.howibuy.services.v1.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import eu.tealhelix.howibuy.services.model.FoodTerm;
import eu.tealhelix.howibuy.services.model.ImmutableFoodTerm;
import org.junit.jupiter.api.Test;

class FoodTermMatcherTest {
	private final TextNormalizer normalizer = new DefaultTextNormalizer();

	@Test
	void findsTheFoodNounAmongBrandAndQuantityNoise() {
		var glossary = List.of(term("Ανθότυρος"), term("Γραβιέρα"));
		var matches = FoodTermMatcher.match(glossary, "Amari Ανθότυρος Ξηρός 250g", normalizer);
		assertEquals(List.of("Ανθότυρος"), terms(matches), "the food noun is found among the brand and quantity tokens");
	}

	@Test
	void ignoresCaseAndDiacritics() {
		var glossary = List.of(term("Βλήτα"));
		assertEquals(List.of("Βλήτα"), terms(FoodTermMatcher.match(glossary, "ΒΛΗΤΑ ΕΛΛΗΝΙΚΑ", normalizer)), "uppercase with accents dropped still matches");
		assertEquals(List.of("Βλήτα"), terms(FoodTermMatcher.match(glossary, "βλητα ελληνικα", normalizer)), "lowercase and unaccented still matches");
	}

	@Test
	void doesNotBleedIntoAnInflectedToken() {
		var glossary = List.of(term("τυρί"));
		assertEquals(List.of(), terms(FoodTermMatcher.match(glossary, "τυριού φέτα", normalizer)), "token-exact matching does not match inside the inflected 'τυριού'");
	}

	@Test
	void matchesAContiguousMultiWordTermRespectingOrder() {
		var glossary = List.of(term("παρθένο ελαιόλαδο"));
		assertEquals(List.of("παρθένο ελαιόλαδο"), terms(FoodTermMatcher.match(glossary, "Εξτρα παρθένο ελαιόλαδο 1L", normalizer)), "contiguous phrase matches");
		assertEquals(List.of(), terms(FoodTermMatcher.match(glossary, "ελαιόλαδο παρθένο", normalizer)), "reordered phrase does not match");
	}

	@Test
	void returnsAllMatchesMostSpecificFirst() {
		var glossary = List.of(term("τυρί"), term("κρέμα τυρί"));
		assertEquals(List.of("κρέμα τυρί", "τυρί"), terms(FoodTermMatcher.match(glossary, "Κρέμα τυρί 200g", normalizer)), "the longer, more specific term comes first");
	}

	@Test
	void returnsNothingForEmptyInputs() {
		assertEquals(List.of(), terms(FoodTermMatcher.match(List.of(), "Ανθότυρος", normalizer)), "empty glossary yields no matches");
		assertEquals(List.of(), terms(FoodTermMatcher.match(List.of(term("Ανθότυρος")), "", normalizer)), "empty product name yields no matches");
	}

	@Test
	void honorsTheGivenNormalizersLanguageFolding() {
		var glossary = List.of(term("Ανθότυροσ"));
		assertEquals(List.of("Ανθότυροσ"), terms(FoodTermMatcher.match(glossary, "ΑΝΘΟΤΥΡΟΣ Κρήτης", new GreekTextNormalizer())), "the Greek normalizer folds ς/σ so the term matches");
		assertEquals(List.of(), terms(FoodTermMatcher.match(glossary, "ΑΝΘΟΤΥΡΟΣ Κρήτης", new DefaultTextNormalizer())), "the default normalizer leaves ς/σ distinct, so no match");
	}

	private static List<String> terms(List<FoodTerm> matches) {
		return matches.stream().map(FoodTerm::getTerm).toList();
	}

	private static FoodTerm term(String term) {
		return ImmutableFoodTerm.builder().term(term).canonicalEn(term).description(term).build();
	}
}
