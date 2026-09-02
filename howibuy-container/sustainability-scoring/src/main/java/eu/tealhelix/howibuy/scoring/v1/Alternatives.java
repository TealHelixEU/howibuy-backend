package eu.tealhelix.howibuy.scoring.v1;

import java.util.Optional;

/**
 * What the search found for one scanned product: the best substitute under each of the three criteria. They rank the
 * same set of candidates three ways and frequently disagree, which is the point of returning all three.
 * <p>
 * A winner may be the reference product itself, which is how the search says that nothing eligible beats what the
 * user already has. Callers tell the two apart by comparing against {@link #reference()}.
 * <p>
 * All three are absent together, when no category is substitutable for the reference product's at the configured
 * level. That is an answer, not a failure.
 */
public record Alternatives(
		ScoredProduct reference,
		Optional<ScoredProduct> bestPersonal,
		Optional<ScoredProduct> bestScientific,
		Optional<ScoredProduct> bestCombined) {

	static Alternatives none(ScoredProduct reference) {
		return new Alternatives(reference, Optional.empty(), Optional.empty(), Optional.empty());
	}
}
