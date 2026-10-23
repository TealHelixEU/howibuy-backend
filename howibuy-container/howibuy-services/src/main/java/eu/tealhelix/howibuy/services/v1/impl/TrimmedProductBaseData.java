package eu.tealhelix.howibuy.services.v1.impl;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import eu.tealhelix.howibuy.v1.model.ProductBaseData;

/**
 * Product data with its text trimmed and its Unicode composed, as the classification input hash of the Ingestion takes
 * it, so that a product is classified from the same text whatever whitespace or composition it came with.
 */
final class TrimmedProductBaseData implements ProductBaseData {
	private final Locale language;
	private final String name;
	private final Map<String, String> characteristics;
	private final List<String> tags;

	private TrimmedProductBaseData(Locale language, String name, Map<String, String> characteristics, List<String> tags) {
		this.language = language;
		this.name = name;
		this.characteristics = characteristics;
		this.tags = tags;
	}

	/**
	 * Two characteristics whose keys are equal once trimmed become one, the latter.
	 */
	static TrimmedProductBaseData of(String language, String name, Map<String, String> characteristics, List<String> tags) {
		var trimmedCharacteristics = new LinkedHashMap<String, String>();
		characteristics.forEach((key, value) -> trimmedCharacteristics.put(trimmed(key), trimmed(value)));
		return new TrimmedProductBaseData(
				Locale.forLanguageTag(language),
				trimmed(name),
				trimmedCharacteristics,
				tags.stream().map(TrimmedProductBaseData::trimmed).toList()
		);
	}

	private static String trimmed(String text) {
		return Normalizer.normalize(text.trim(), Normalizer.Form.NFC);
	}

	@Override
	public Locale getLanguage() {
		return language;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Map<String, String> getCharacteristics() {
		return characteristics;
	}

	@Override
	public List<String> getTags() {
		return tags;
	}
}
