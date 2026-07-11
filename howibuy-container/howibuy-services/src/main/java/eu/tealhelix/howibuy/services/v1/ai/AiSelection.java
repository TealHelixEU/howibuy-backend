package eu.tealhelix.howibuy.services.v1.ai;

/**
 * The AI's choice among a candidate list it was shown. The candidates are presented to the model as a numbered list and
 * the model answers with the number of its pick, so a selection is carried back as a numeric index rather than the
 * candidate's text.
 * <p>
 * Identifying the pick by position, not by echoing its name, is deliberate: a model asked to repeat a candidate name
 * verbatim can alter it in ways that render identically but differ byte-for-byte — a Latin {@code M} swapped for a Greek
 * {@code Μ}, smart quotes, a trailing period, casing drift — which would then fail an exact match against the
 * candidate. A single number has no such surface, so the match is robust as long as the number is in range.
 */
public sealed interface AiSelection {

	/** The AI picked the candidate at this {@code 0}-based index into the list it was shown. */
	record Match(int index) implements AiSelection {}

	/** The AI reported that no candidate fits the product. */
	record None() implements AiSelection {}

	/**
	 * The AI's reply was neither the no-match token nor a candidate number within range — it did not honour the prompt
	 * contract. {@code rawReply} is the trimmed reply, kept for diagnostics.
	 */
	record Malformed(String rawReply) implements AiSelection {}
}
