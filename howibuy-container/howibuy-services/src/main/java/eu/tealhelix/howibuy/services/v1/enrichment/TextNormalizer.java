package eu.tealhelix.howibuy.services.v1.enrichment;

/**
 * Normalizes text for token matching, applied identically to product names and glossary terms. Implementations strip
 * diacritics, lower-case and reduce the text to space-separated alphanumeric tokens, plus any language-specific folding
 * (e.g. Greek final sigma). One implementation exists per language that needs special handling; languages without a
 * quirk use {@link DefaultTextNormalizer}.
 */
interface TextNormalizer {
	String normalize(String text);
}
