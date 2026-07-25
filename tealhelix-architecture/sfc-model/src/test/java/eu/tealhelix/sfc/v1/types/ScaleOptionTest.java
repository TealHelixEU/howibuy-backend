package eu.tealhelix.sfc.v1.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * The five-point scale enum: each point carries its stored 1–5 ordinal, and a stored ordinal maps back to its point so
 * a persisted answer can be rebuilt. Out-of-range ordinals — which the database's {@code CHECK 1..5} would never store —
 * are rejected rather than silently coerced.
 */
class ScaleOptionTest {
	@Test
	void everyPointCarriesItsOneToFiveOrdinal() {
		assertEquals(1, ScaleOption.NOT_IMPORTANT.getValue());
		assertEquals(2, ScaleOption.SLIGHTLY_IMPORTANT.getValue());
		assertEquals(3, ScaleOption.MODERATELY_IMPORTANT.getValue());
		assertEquals(4, ScaleOption.VERY_IMPORTANT.getValue());
		assertEquals(5, ScaleOption.EXTREMELY_IMPORTANT.getValue());
	}

	@Test
	void fromValueRebuildsThePointFromItsStoredOrdinal() {
		for (var option : ScaleOption.values()) {
			assertSame(option, ScaleOption.fromValue(option.getValue()), () -> "round-trips " + option);
		}
	}

	@Test
	void fromValueRejectsAnOrdinalOutsideOneToFive() {
		assertThrows(IllegalArgumentException.class, () -> ScaleOption.fromValue((short) 0));
		assertThrows(IllegalArgumentException.class, () -> ScaleOption.fromValue((short) 6));
	}
}
