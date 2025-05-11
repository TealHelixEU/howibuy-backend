package eu.tealhelix.common.types.entity;

import eu.tealhelix.common.types.RepresentableAsString;

/**
 * Abstract superclass of all exceptions that have to do with the state of an entity in the application.
 */
public abstract class AppEntityException extends RuntimeException {
	protected RepresentableAsString entityId;

	public AppEntityException() {
	}

	public AppEntityException(RepresentableAsString entityId) {
		this.entityId = entityId;
	}

	public AppEntityException(String message) {
		super(message);
	}

	public AppEntityException(RepresentableAsString entityId, String message) {
		super(message);
		this.entityId = entityId;
	}

	public AppEntityException(Throwable cause) {
		super(cause);
	}

	public AppEntityException(RepresentableAsString entityId, Throwable cause) {
		super(cause);
		this.entityId = entityId;
	}

	public AppEntityException(String message, Throwable cause) {
		super(message, cause);
	}

	public AppEntityException(RepresentableAsString entityId, String message, Throwable cause) {
		super(message, cause);
		this.entityId = entityId;
	}

	public RepresentableAsString getEntityId() {
		return entityId;
	}
}
