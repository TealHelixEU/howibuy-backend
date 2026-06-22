package eu.tealhelix.common.types.validation;

public class RequiredInputMissingException extends AppValidationException {
	public static RequiredInputMissingException fromRequiredInputName(String inputName) {
		var e = new RequiredInputMissingException("Required input '" + inputName + "' is missing.");
		e.inputName = inputName;
		return e;
	}

	public static void throwIfRequiredInputMissing(String inputName, String value) {
		if (value == null || value.isEmpty()) {
			throw fromRequiredInputName(inputName);
		}
	}

	private String inputName;

	public RequiredInputMissingException(String message) {
		super(message);
	}

	public String getInputName() {
		return inputName;
	}
}
