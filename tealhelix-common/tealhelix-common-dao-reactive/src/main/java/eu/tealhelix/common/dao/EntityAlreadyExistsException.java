package eu.tealhelix.common.dao;

/**
 * An entity could not be stored because one already stored holds a value that must be unique.
 */
public class EntityAlreadyExistsException extends DaoException {
	public EntityAlreadyExistsException(String message, Throwable cause) {
		super(message, cause);
	}
}
