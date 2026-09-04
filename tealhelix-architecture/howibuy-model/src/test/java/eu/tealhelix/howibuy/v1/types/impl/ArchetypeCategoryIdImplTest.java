package eu.tealhelix.howibuy.v1.types.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import eu.tealhelix.howibuy.v1.types.ArchetypeCategoryId;
import org.junit.jupiter.api.Test;

/**
 * The archetype-category-id value type: it round-trips its string, parses to a {@link UUID}, and compares structurally
 * by that string. It names a node of the SAFAD taxonomy at any of its three levels, since the levels are one kind of
 * thing and a category's own id is what its children are fetched by.
 */
class ArchetypeCategoryIdImplTest {
	private static final String UUID_TEXT = "33333333-3333-3333-3333-333333333333";

	@Test
	void asStringReturnsTheRepresentation() {
		assertEquals(UUID_TEXT, new ArchetypeCategoryIdImpl(UUID_TEXT).asString());
	}

	@Test
	void asUuidParsesTheRepresentation() {
		assertEquals(UUID.fromString(UUID_TEXT), new ArchetypeCategoryIdImpl(UUID_TEXT).asUuid());
	}

	@Test
	void equalsAnotherArchetypeCategoryIdWithTheSameRepresentation() {
		ArchetypeCategoryId one = new ArchetypeCategoryIdImpl(UUID_TEXT);
		ArchetypeCategoryId two = new ArchetypeCategoryIdImpl(UUID_TEXT);
		assertEquals(one, two);
		assertEquals(one.hashCode(), two.hashCode());
	}

	@Test
	void differsFromAnArchetypeCategoryIdWrappingAnotherUuid() {
		assertNotEquals(new ArchetypeCategoryIdImpl(UUID_TEXT), new ArchetypeCategoryIdImpl("44444444-4444-4444-4444-444444444444"));
	}

	@Test
	void rejectsANullRepresentation() {
		assertThrows(NullPointerException.class, () -> new ArchetypeCategoryIdImpl(null));
	}
}
