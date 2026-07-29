package eu.tealhelix.sfc.services.v1.impl;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.sfc.v1.types.ScaleOption;

/**
 * The localized labels for the five Likert {@link ScaleOption}s. These are fixed UI chrome, not authored content, so
 * they live in a {@code .properties} resource bundle keyed by the enum constant names — not in the database (ADR 0002).
 * The bundle files sit next to this class ({@code ScaleOptionLabels[_xx].properties}); the root bundle holds English, so
 * a language with no bundle of its own (e.g. {@code en}) resolves to it. The bundle's languages must stay in sync with
 * {@code sfc.languages}, guaranteed by the content authors.
 */
@ApplicationScoped
public class ScaleOptionLabels {
	private static final String BUNDLE = ScaleOptionLabels.class.getName();

	/**
	 * Property-file bundles only, and no fall-back to the server's default locale: an unresolved language drops to the
	 * root (English) bundle, never to whatever locale the JVM happens to run in.
	 */
	private static final ResourceBundle.Control CONTROL =
			ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

	/**
	 * The five scale labels localized for {@code language} (an ISO 639-1 code already resolved to a supported one).
	 */
	public Map<ScaleOption, String> forLanguage(String language) {
		var bundle = ResourceBundle.getBundle(BUNDLE, Locale.of(language), CONTROL);
		var labels = new EnumMap<ScaleOption, String>(ScaleOption.class);
		for (var option : ScaleOption.values()) {
			labels.put(option, bundle.getString(option.name()));
		}
		return labels;
	}
}
