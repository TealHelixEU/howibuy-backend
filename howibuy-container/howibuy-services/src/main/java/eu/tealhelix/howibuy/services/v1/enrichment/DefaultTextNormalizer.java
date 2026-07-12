package eu.tealhelix.howibuy.services.v1.enrichment;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The language-neutral normalization shared by every language: strip diacritics (via NFD decomposition), lower-case,
 * and reduce runs of non-alphanumeric characters to single spaces. Latin diacritics of the Baltic languages fall out
 * of the NFD stripping, so those languages need no dedicated normalizer.
 */
final class DefaultTextNormalizer implements TextNormalizer {
	@Override
	public String normalize(String text) {
		var withoutMarks = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return withoutMarks.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
	}
}
