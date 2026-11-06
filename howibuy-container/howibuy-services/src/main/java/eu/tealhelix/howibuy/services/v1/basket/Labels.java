package eu.tealhelix.howibuy.services.v1.basket;

import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The labels of the constants of an enum, shown to the user, in a {@code .properties} resource bundle keyed by the
 * constant names. The bundle files sit in this package, one bundle per enum ({@code <Enum>Labels[_xx].properties}); the
 * root bundle holds English. The business people supply the wording.
 * <p>
 * English stands in for a language with no bundle of its own, and for a constant a bundle does not translate, which is
 * logged as a warning.
 *
 * @param <E> the enum whose constants are labelled
 */
public final class Labels<E extends Enum<E>> {
	private static final Logger LOG = LoggerFactory.getLogger(Labels.class);

	/**
	 * Property-file bundles only, and no fall-back to the server's default locale: an unresolved language drops to the
	 * root (English) bundle, never to whatever locale the JVM happens to run in.
	 */
	private static final ResourceBundle.Control CONTROL =
			ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

	private final String bundleBaseName;

	/**
	 * @param bundleBaseName the fully qualified base name of the bundle, as {@link ResourceBundle#getBundle} takes it
	 */
	public Labels(String bundleBaseName) {
		this.bundleBaseName = bundleBaseName;
	}

	/**
	 * The label of {@code constant} in {@code language} (an ISO 639-1 code), English if there is no translation.
	 */
	public String labelOf(E constant, String language) {
		var bundle = ResourceBundle.getBundle(bundleBaseName, Locale.of(language), CONTROL);
		if (isTranslation(bundle, language) && ((PropertyResourceBundle) bundle).handleGetObject(constant.name()) == null) {
			LOG.warn("Label not translated, falling back to English, bundle, language and key: {} {} {}", bundleBaseName, language, constant.name());
		}
		return bundle.getString(constant.name());
	}

	/**
	 * Whether the bundle resolved is the one of the language rather than the English root.
	 */
	private static boolean isTranslation(ResourceBundle bundle, String language) {
		return !bundle.getLocale().equals(Locale.ROOT) && bundle.getLocale().getLanguage().equals(language);
	}
}
