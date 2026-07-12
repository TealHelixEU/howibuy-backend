package eu.tealhelix.howibuy.services.v1.enrichment;

/**
 * Greek normalization: the {@link DefaultTextNormalizer default} plus folding the word-final sigma {@code ς} onto the
 * regular {@code σ}, so the two sigma forms of the same word match regardless of position or how a term was typed.
 */
final class GreekTextNormalizer implements TextNormalizer {
	private static final char FINAL_SIGMA = 'ς';
	private static final char SIGMA = 'σ';

	private final TextNormalizer base = new DefaultTextNormalizer();

	@Override
	public String normalize(String text) {
		return base.normalize(text).replace(FINAL_SIGMA, SIGMA);
	}
}
