package eu.tealhelix.howibuy.services.v1.types;

import eu.tealhelix.howibuy.v1.model.MatchDiagnostics;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;

/**
 * How the matching of one product to an Archetype Product ended, with the path it took through the taxonomy. The type
 * is {@code sealed} so its callers switch over the cases exhaustively: adding a case becomes a compile error until
 * every caller handles it.
 */
public sealed interface ArchetypeMatchOutcome {
	MatchDiagnostics diagnostics();

	/**
	 * The descent reached an Archetype Product.
	 */
	record Matched(ArchetypeProductId archetypeProductId, MatchDiagnostics diagnostics) implements ArchetypeMatchOutcome {
	}

	/**
	 * The AI deliberately reported no match (its reply was the {@code NONE} sentinel) at some taxonomy level. This is a
	 * legitimate business outcome: the product matches none of our archetypes.
	 */
	record NoMatch(MatchDiagnostics diagnostics) implements ArchetypeMatchOutcome {
	}

	/**
	 * The AI's reply was neither the no-match token nor a candidate number within the range it was shown: it did not
	 * honour the prompt contract. Unlike {@link NoMatch} this is not a business no-match but an AI malfunction.
	 *
	 * @param kind     what the AI was asked to pick when it replied, {@code category} or {@code product}
	 * @param rawReply the reply of the AI
	 */
	record Malformed(MatchDiagnostics diagnostics, String kind, String rawReply) implements ArchetypeMatchOutcome {
	}
}
