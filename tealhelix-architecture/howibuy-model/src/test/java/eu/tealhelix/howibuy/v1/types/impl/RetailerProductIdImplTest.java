package eu.tealhelix.howibuy.v1.types.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import eu.tealhelix.howibuy.v1.types.RetailerProductId;
import org.junit.jupiter.api.Test;

/**
 * The retailer-product-id value type: it round-trips its string, parses to a {@link UUID}, and compares structurally
 * by that string, while staying distinct from an {@code ArchetypeProductId} wrapping the very same UUID.
 */
class RetailerProductIdImplTest {
	private static final String UUID_TEXT = "11111111-1111-1111-1111-111111111111";

	@Test
	void asStringReturnsTheRepresentation() {
		assertEquals(UUID_TEXT, new RetailerProductIdImpl(UUID_TEXT).asString());
	}

	@Test
	void asUuidParsesTheRepresentation() {
		assertEquals(UUID.fromString(UUID_TEXT), new RetailerProductIdImpl(UUID_TEXT).asUuid());
	}

	@Test
	void equalsAnotherRetailerProductIdWithTheSameRepresentation() {
		RetailerProductId one = new RetailerProductIdImpl(UUID_TEXT);
		RetailerProductId two = new RetailerProductIdImpl(UUID_TEXT);
		assertEquals(one, two);
		assertEquals(one.hashCode(), two.hashCode());
	}

	@Test
	void differsFromARetailerProductIdWrappingAnotherUuid() {
		assertNotEquals(new RetailerProductIdImpl(UUID_TEXT), new RetailerProductIdImpl("22222222-2222-2222-2222-222222222222"));
	}

	@Test
	void isNotEqualToAnArchetypeProductIdWithTheSameUuid() {
		assertNotEquals(new RetailerProductIdImpl(UUID_TEXT), new ArchetypeProductIdImpl(UUID_TEXT));
		assertNotEquals(new ArchetypeProductIdImpl(UUID_TEXT), new RetailerProductIdImpl(UUID_TEXT));
	}

	@Test
	void rejectsANullRepresentation() {
		assertThrows(NullPointerException.class, () -> new RetailerProductIdImpl(null));
	}
}
