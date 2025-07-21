package eu.tealhelix.common.types;

import eu.tealhelix.common.types.impl.EmailImpl;

/**
 * Representation of an email in the application.
 */
public interface Email extends RepresentableAsString {
	static Email of(String email) {
		return new EmailImpl(email);
	}
}
