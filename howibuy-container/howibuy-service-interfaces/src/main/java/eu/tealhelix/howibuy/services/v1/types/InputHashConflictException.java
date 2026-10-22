package eu.tealhelix.howibuy.services.v1.types;

import eu.tealhelix.howibuy.v1.types.HasRetailerProductId;

/**
 * Thrown when a Classification Attempt was made from another classification input hash than the product's current
 * one, because the product changed while it was being classified. Nothing is recorded (mapped to HTTP 409).
 */
public class InputHashConflictException extends RuntimeException {
	public InputHashConflictException(HasRetailerProductId productId, String inputHash) {
		super("The input hash is not the product's current one, product id: " + productId.getId().asString() + ", inputHash: " + inputHash);
	}
}
