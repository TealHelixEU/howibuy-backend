package eu.tealhelix.howibuy.services.v1.ai.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import eu.tealhelix.howibuy.services.v1.ai.AiSelection;
import org.junit.jupiter.api.Test;

class RenderingHelperTest {

	@Test
	void renderCandidatesNumbersThemStartingAtOne() {
		assertEquals("1. Apples\n2. Bananas\n3. Cherries",
				RenderingHelper.renderCandidates(List.of("Apples", "Bananas", "Cherries")));
	}

	@Test
	void renderCandidatesRejectsAnEmptyList() {
		assertThrows(IllegalArgumentException.class, () -> RenderingHelper.renderCandidates(List.of()));
	}

	@Test
	void parsesANumberIntoTheZeroBasedIndex() {
		assertEquals(new AiSelection.Match(13), RenderingHelper.parseSelection("14", 20));
		assertEquals(new AiSelection.Match(0), RenderingHelper.parseSelection("1", 4));
	}

	@Test
	void parsesANumberDespiteSurroundingWhitespaceOrTrailingText() {
		assertEquals(new AiSelection.Match(1), RenderingHelper.parseSelection("  2  ", 4));
		assertEquals(new AiSelection.Match(0), RenderingHelper.parseSelection("1.", 4));
		assertEquals(new AiSelection.Match(0), RenderingHelper.parseSelection("1. Orange juice", 4));
	}

	@Test
	void parsesTheNoMatchTokenIntoNone() {
		assertEquals(new AiSelection.None(), RenderingHelper.parseSelection("NONE", 20));
		assertEquals(new AiSelection.None(), RenderingHelper.parseSelection("none", 20));
		assertEquals(new AiSelection.None(), RenderingHelper.parseSelection("NONE.", 20));
	}

	@Test
	void treatsAnOutOfRangeNumberAsMalformed() {
		assertEquals(new AiSelection.Malformed("0"), RenderingHelper.parseSelection("0", 4));
		assertEquals(new AiSelection.Malformed("5"), RenderingHelper.parseSelection("5", 4));
	}

	@Test
	void treatsAReplyWithoutALeadingNumberAsMalformed() {
		assertEquals(new AiSelection.Malformed("Milk and dairy products"),
				RenderingHelper.parseSelection("Milk and dairy products", 20));
	}

	@Test
	void treatsAnEmptyOrNullReplyAsMalformed() {
		assertEquals(new AiSelection.Malformed(""), RenderingHelper.parseSelection("", 4));
		assertEquals(new AiSelection.Malformed(""), RenderingHelper.parseSelection(null, 4));
	}
}
