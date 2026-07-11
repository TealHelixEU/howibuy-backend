package eu.tealhelix.howibuy.services.v1.ai.impl;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import eu.tealhelix.howibuy.services.v1.ai.AiSelection;

/**
 * Helper class for rendering the model structures to strings that will be sent to the AI, and for parsing the AI's
 * reply back.
 * <p>
 * It owns the candidate-selection protocol in both directions: {@link #renderCandidates(List)} presents the candidates
 * as a {@code 1}-based numbered list and {@link #parseSelection(String, int)} reads the number the AI answers with back
 * into a {@code 0}-based index. Keeping both here means the numbering convention lives in exactly one place.
 */
class RenderingHelper {
	public static final String CHARACTERISTICS_HEADER = "### Product Characteristics\n\n";
	public static final String TAGS_HEADER = "### Product Tags\n\n";

	static final String NO_MATCH_TOKEN = "NONE";
	private static final Pattern LEADING_INDEX = Pattern.compile("^\\s*(\\d{1,6})");

	private RenderingHelper() {
		// This is a utility class, not to be instantiated
	}

	public static String renderTheProductCharacteristics(Map<String, String> characteristics) {
		if (characteristics == null || characteristics.isEmpty()) return "";
		return characteristics.entrySet().stream()
				.map(e -> "- " + e.getKey() + ": " + e.getValue())
				.collect(Collectors.joining("\n", CHARACTERISTICS_HEADER, "\n"));
	}

	public static String renderTheProductTags(List<String> tags) {
		if (tags == null || tags.isEmpty()) return "";
		return tags.stream()
				.map(t -> "- " + t)
				.collect(Collectors.joining("\n", TAGS_HEADER, "\n"));
	}

	public static String renderCandidates(List<String> candidates) {
		if (candidates == null || candidates.isEmpty()) {
			throw new IllegalArgumentException("candidates must not be null or empty");
		}
		return IntStream.range(0, candidates.size())
				.mapToObj(i -> (i + 1) + ". " + candidates.get(i))
				.collect(Collectors.joining("\n"));
	}

	/**
	 * Parses the AI's reply to a candidate list of the given size. A reply of {@link #NO_MATCH_TOKEN} (allowing
	 * surrounding punctuation) is {@link AiSelection.None}; a reply beginning with a number in {@code [1, candidateCount]}
	 * is a {@link AiSelection.Match} on that number's {@code 0}-based index; anything else is {@link AiSelection.Malformed}.
	 */
	public static AiSelection parseSelection(String reply, int candidateCount) {
		String trimmed = reply == null ? "" : reply.trim();
		if (isNoMatch(trimmed)) return new AiSelection.None();
		Matcher matcher = LEADING_INDEX.matcher(trimmed);
		if (matcher.find()) {
			int oneBased = Integer.parseInt(matcher.group(1));
			if (oneBased >= 1 && oneBased <= candidateCount) return new AiSelection.Match(oneBased - 1);
		}
		return new AiSelection.Malformed(trimmed);
	}

	private static boolean isNoMatch(String trimmed) {
		return trimmed.replaceAll("[^A-Za-z]", "").equalsIgnoreCase(NO_MATCH_TOKEN);
	}
}
