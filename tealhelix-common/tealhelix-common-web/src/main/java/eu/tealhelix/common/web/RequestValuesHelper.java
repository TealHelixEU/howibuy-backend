package eu.tealhelix.common.web;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;

/**
 * The conversions of the values a request carries into the types the services take, refusing a malformed value with
 * the name of its input. A missing value is refused only by {@link #required} and {@link #requiredText}, which a
 * resource uses for what it cannot convert without; the rest it converts to {@code null}, for the service to check.
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
	 * @return The text converted, or {@code null} when it is missing, {@code null} or empty as {@link #requiredText}
	 *         takes it
	 */
	static <T> T nullable(String value, Function<String, T> conversion) {
		return value == null || value.isEmpty() ? null : conversion.apply(value);
	}

	/**
	 * @return The value, written as a UUID is written, or {@code null} when it is missing
	 */
	static String uuid(String inputName, String value) {
		return nullable(value, text -> {
			try {
				return UUID.fromString(text).toString();
			} catch (IllegalArgumentException e) {
				throw BadInputValueException.fromInputNameAndHint(inputName, "It must be a UUID");
			}
		});
	}

	/**
	 * The builder of a model object refuses a value against the model's rules with an {@code IllegalStateException},
	 * which is refused here as an illegal input, hinted by the builder's message.
	 *
	 * @param inputName The input of the request the object is built from
	 * @param build     The call to the builder's {@code build()}, and nothing else, so that no other failure is taken
	 *                  for an illegal input
	 * @return The object built
	 */
	static <T> T built(String inputName, Supplier<T> build) {
		try {
			return build.get();
		} catch (IllegalStateException e) {
			throw BadInputValueException.fromInputNameAndHint(inputName, e.getMessage());
		}
	}
}
