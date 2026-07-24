package eu.tealhelix.sfc.services.v1.impl;

import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.types.validation.BadInputValueException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The configured closed set of languages the compass content is served in, and the default used when a request omits
 * the language. Resolves a requested language to the one to serve, rejecting anything outside the supported set — there
 * is no silent per-field fallback (ADR 0002).
 */
@ApplicationScoped
public class SfcLanguages {
	private final Set<String> supported;
	private final String defaultLanguage;

	@Inject
	public SfcLanguages(
			@ConfigProperty(name = "sfc.languages") Set<String> supported,
			@ConfigProperty(name = "sfc.default-language") String defaultLanguage) {
		this.supported = Set.copyOf(supported);
		this.defaultLanguage = defaultLanguage;
	}

	/**
	 * The language to serve for a requested language: the {@link #defaultLanguage} when {@code requested} is null or
	 * blank, {@code requested} itself when it is supported, otherwise a {@link BadInputValueException} (mapped to HTTP
	 * 400).
	 */
	public String resolve(String requested) {
		if (requested == null || requested.isBlank()) {
			return defaultLanguage;
		}
		if (!supported.contains(requested)) {
			throw BadInputValueException.fromInputNameAndHint("lang", "The supported languages are: " + supported);
		}
		return requested;
	}
}
