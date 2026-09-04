package eu.tealhelix.howibuy.v1.types.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import org.junit.jupiter.api.Test;

/**
 * The archetype-product-id value type: it round-trips its string, parses to a {@link UUID}, and compares structurally
 * by that string — while staying distinct from an {@code ArchetypeCategoryId} wrapping the very same UUID, which is
 * the type safety this value type exists to provide.
 */
class ArchetypeProductIdImplTest {
	private static final String UUID_TEXT = "11111111-1111-1111-1111-111111111111";

	@Test
	void asStringReturnsTheRepresentation() {
		assertEquals(UUID_TEXT, new ArchetypeProductIdImpl(UUID_TEXT).asString());
	}

	@Test
	void asUuidParsesTheRepresentation() {
		assertEquals(UUID.fromString(UUID_TEXT), new ArchetypeProductIdImpl(UUID_TEXT).asUuid());
	}

	@Test
	void equalsAnotherArchetypeProductIdWithTheSameRepresentation() {
		ArchetypeProductId one = new ArchetypeProductIdImpl(UUID_TEXT);
		ArchetypeProductId two = new ArchetypeProductIdImpl(UUID_TEXT);
		assertEquals(one, two);
		assertEquals(one.hashCode(), two.hashCode());
	}

	@Test
	void differsFromAnArchetypeProductIdWrappingAnotherUuid() {
		assertNotEquals(new ArchetypeProductIdImpl(UUID_TEXT), new ArchetypeProductIdImpl("22222222-2222-2222-2222-222222222222"));
	}

	@Test
	void isNotEqualToAnArchetypeCategoryIdWithTheSameUuid() {
		assertNotEquals(new ArchetypeProductIdImpl(UUID_TEXT), new ArchetypeCategoryIdImpl(UUID_TEXT));
		assertNotEquals(new ArchetypeCategoryIdImpl(UUID_TEXT), new ArchetypeProductIdImpl(UUID_TEXT));
	}

	@Test
	void rejectsANullRepresentation() {
		assertThrows(NullPointerException.class, () -> new ArchetypeProductIdImpl(null));
	}
}
