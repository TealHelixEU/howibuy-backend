package eu.tealhelix.common.types.validation;

public class BadInputValueException extends AppValidationException {
	public static BadInputValueException fromInputName(String inputName) {
		var e = new BadInputValueException("The value of input '" + inputName + "' is illegal.");
		e.inputName = inputName;
		return e;
	}

	public static BadInputValueException fromInputNameAndHint(String inputName, String hint) {
		var e = fromInputName(inputName);
		e.hint = hint;
		return e;
	}

	public static void throwForStringMaxLength(String inputName, String value, int maxLength) {
		if (value != null && value.length() > maxLength) {
			throw fromInputNameAndHint(inputName, "The length should be less than " + maxLength);
		}
	}

	private String inputName;
	private String hint;

	public BadInputValueException(String message) {
		super(message);
	}

	public String getInputName() {
		return inputName;
	}

	public String getHint() {
		return hint;
	}
}
