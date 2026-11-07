package eu.tealhelix.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import eu.tealhelix.common.types.validation.BadInputValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class RequestValuesHelperTest {
	@Test
	void nullableConvertsTheText() {
		assertEquals(Integer.valueOf(42), RequestValuesHelper.nullable("42", Integer::valueOf));
	}

	@ParameterizedTest
	@NullAndEmptySource
	void nullableTakesMissingTextForNull(String value) {
		assertNull(RequestValuesHelper.nullable(value, Integer::valueOf));
	}

	@Test
	void uuidWritesTheValueAsAUuidIsWritten() {
		assertEquals("7c1e2f4a-5b6d-4e8f-9a0b-1c2d3e4f5a61", RequestValuesHelper.uuid("id", "7C1E2F4A-5B6D-4E8F-9A0B-1C2D3E4F5A61"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	void uuidTakesMissingTextForNull(String value) {
		assertNull(RequestValuesHelper.uuid("id", value));
	}

	@Test
	void uuidRefusesAValueThatIsNotAUuid() {
		var e = assertThrows(BadInputValueException.class, () -> RequestValuesHelper.uuid("id", "not-a-uuid"));

		assertEquals("id", e.getInputName());
		assertEquals("It must be a UUID", e.getHint());
	}

	@Test
	void builtAnswersWhatTheBuilderBuilt() {
		assertEquals("built", RequestValuesHelper.built("products", () -> "built"));
	}

	@Test
	void builtRefusesWhatTheBuilderRefusesAsAnIllegalInput() {
		var e = assertThrows(BadInputValueException.class, () -> RequestValuesHelper.built("products", () -> {
			throw new IllegalStateException("Cannot build ProductChange, some of required attributes are not set [productKey]");
		}));

		assertEquals("products", e.getInputName());
		assertEquals("Cannot build ProductChange, some of required attributes are not set [productKey]", e.getHint());
	}

	@Test
	void builtLetsAnyOtherFailureThrough() {
		var failure = new IllegalArgumentException("A bug");

		assertEquals(failure, assertThrows(IllegalArgumentException.class, () -> RequestValuesHelper.built("products", () -> {
			throw failure;
		})));
	}
}
