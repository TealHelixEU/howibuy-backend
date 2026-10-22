package eu.tealhelix.howibuy.jaxrs.v1;

import java.util.UUID;

import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;

/**
 * The checks of the values a request carries, refusing a missing or malformed one with the name of its input.
 */
public interface RequestValuesHelper {
	static <T> T required(String inputName, T value) {
		if (value == null) throw RequiredInputMissingException.fromRequiredInputName(inputName);
		return value;
	}

	static String requiredText(String inputName, String value) {
		RequiredInputMissingException.throwIfRequiredInputMissing(inputName, value);
		return value;
	}

	/**
	 * @return The value, written as a UUID is written
	 */
	static String uuid(String inputName, String value) {
		try {
			return UUID.fromString(requiredText(inputName, value)).toString();
		} catch (IllegalArgumentException e) {
			throw BadInputValueException.fromInputNameAndHint(inputName, "It must be a UUID");
		}
	}
}
