package eu.tealhelix.howibuy.v1.types;

/**
 * Who made a Classification Attempt, in order of precedence: the attempts of a classifier outweigh those of the ones
 * after it.
 */
public enum Classifier {
	/** One of us, such as an operator correcting a Classification or settling one flagged for review. */
	MANUAL,

	/** A domain source we trust but do not run, such as WP3. */
	EXPERT,

	/** The language model HowiBuy runs. */
	LLM
}
