package eu.tealhelix.common.types.entity;

import eu.tealhelix.common.types.RepresentableAsString;

/**
 * The entity referenced by the given entity id, could not be found.
 */
public class NotFoundException extends AppEntityException {
	public NotFoundException(String message) {
		super(message);
	}

	public NotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public NotFoundException(RepresentableAsString entityId) {
		super(entityId);
	}

	public NotFoundException(RepresentableAsString entityId, String message) {
		super(entityId, message);
	}

	public NotFoundException(RepresentableAsString entityId, Throwable cause) {
		super(entityId, cause);
	}

	public NotFoundException(RepresentableAsString entityId, String message, Throwable cause) {
		super(entityId, message, cause);
	}
}
