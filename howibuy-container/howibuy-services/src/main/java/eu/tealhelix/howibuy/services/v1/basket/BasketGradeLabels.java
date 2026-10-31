package eu.tealhelix.howibuy.services.v1.basket;

import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import eu.tealhelix.howibuy.v1.types.BasketGrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The labels of the {@link BasketGrade}s, shown to the user, in a {@code .properties} resource bundle keyed by the
 * enum constant names. The bundle files sit next to this class ({@code BasketGradeLabels[_xx].properties}); the root
 * bundle holds English. The business people supply the wording.
 * <p>
 * English stands in for a language with no bundle of its own, and for a grade a bundle does not translate, which is
 * logged as a warning.
 */
public final class BasketGradeLabels {
	private static final Logger LOG = LoggerFactory.getLogger(BasketGradeLabels.class);

	/**
	 * Property-file bundles only, and no fall-back to the server's default locale: an unresolved language drops to the
	 * root (English) bundle, never to whatever locale the JVM happens to run in.
	 */
	private static final ResourceBundle.Control CONTROL =
			ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

	private final String bundleBaseName;

	public BasketGradeLabels() {
		this(BasketGradeLabels.class.getName());
	}

	BasketGradeLabels(String bundleBaseName) {
		this.bundleBaseName = bundleBaseName;
	}

	/**
	 * The label of {@code grade} in {@code language} (an ISO 639-1 code), English if there is no translation.
	 */
	public String labelOf(BasketGrade grade, String language) {
		var bundle = ResourceBundle.getBundle(bundleBaseName, Locale.of(language), CONTROL);
		if (isTranslation(bundle, language) && ((PropertyResourceBundle) bundle).handleGetObject(grade.name()) == null) {
			LOG.warn("Basket Grade label not translated, falling back to English, language and grade: {} {}", language, grade);
		}
		return bundle.getString(grade.name());
	}

	/**
	 * Whether the bundle resolved is the one of the language rather than the English root.
	 */
	private static boolean isTranslation(ResourceBundle bundle, String language) {
		return !bundle.getLocale().equals(Locale.ROOT) && bundle.getLocale().getLanguage().equals(language);
	}
}
