package eu.tealhelix.sfc.v1.types.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import eu.tealhelix.sfc.v1.types.QuestionId;
import org.junit.jupiter.api.Test;

/**
 * The question-id value type: it round-trips its string, parses to a {@link UUID}, and compares structurally by that
 * string. Cross-type distinctness from {@code CategoryId} is pinned in {@code CategoryIdImplTest}.
 */
class QuestionIdImplTest {
	private static final String UUID_TEXT = "33333333-3333-3333-3333-333333333333";

	@Test
	void asStringReturnsTheRepresentation() {
		assertEquals(UUID_TEXT, new QuestionIdImpl(UUID_TEXT).asString());
	}

	@Test
	void asUuidParsesTheRepresentation() {
		assertEquals(UUID.fromString(UUID_TEXT), new QuestionIdImpl(UUID_TEXT).asUuid());
	}

	@Test
	void equalsAnotherQuestionIdWithTheSameRepresentation() {
		QuestionId one = new QuestionIdImpl(UUID_TEXT);
		QuestionId two = new QuestionIdImpl(UUID_TEXT);
		assertEquals(one, two);
		assertEquals(one.hashCode(), two.hashCode());
	}

	@Test
	void differsFromAQuestionIdWrappingAnotherUuid() {
		assertNotEquals(new QuestionIdImpl(UUID_TEXT), new QuestionIdImpl("44444444-4444-4444-4444-444444444444"));
	}

	@Test
	void rejectsANullRepresentation() {
		assertThrows(NullPointerException.class, () -> new QuestionIdImpl(null));
	}
}
