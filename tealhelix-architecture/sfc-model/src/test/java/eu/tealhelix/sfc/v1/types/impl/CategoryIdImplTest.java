package eu.tealhelix.sfc.v1.types.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import eu.tealhelix.sfc.v1.types.CategoryId;
import org.junit.jupiter.api.Test;

/**
 * The category-id value type: it round-trips its string, parses to a {@link UUID}, and compares structurally by that
 * string — while staying distinct from a {@code QuestionId} wrapping the very same UUID, which is the type safety this
 * value type exists to provide.
 */
class CategoryIdImplTest {
	private static final String UUID_TEXT = "11111111-1111-1111-1111-111111111111";

	@Test
	void asStringReturnsTheRepresentation() {
		assertEquals(UUID_TEXT, new CategoryIdImpl(UUID_TEXT).asString());
	}

	@Test
	void asUuidParsesTheRepresentation() {
		assertEquals(UUID.fromString(UUID_TEXT), new CategoryIdImpl(UUID_TEXT).asUuid());
	}

	@Test
	void equalsAnotherCategoryIdWithTheSameRepresentation() {
		CategoryId one = new CategoryIdImpl(UUID_TEXT);
		CategoryId two = new CategoryIdImpl(UUID_TEXT);
		assertEquals(one, two);
		assertEquals(one.hashCode(), two.hashCode());
	}

	@Test
	void differsFromACategoryIdWrappingAnotherUuid() {
		assertNotEquals(new CategoryIdImpl(UUID_TEXT), new CategoryIdImpl("22222222-2222-2222-2222-222222222222"));
	}

	@Test
	void isNotEqualToAQuestionIdWithTheSameUuid() {
		assertNotEquals(new CategoryIdImpl(UUID_TEXT), new QuestionIdImpl(UUID_TEXT));
		assertNotEquals(new QuestionIdImpl(UUID_TEXT), new CategoryIdImpl(UUID_TEXT));
	}

	@Test
	void rejectsANullRepresentation() {
		assertThrows(NullPointerException.class, () -> new CategoryIdImpl(null));
	}
}
