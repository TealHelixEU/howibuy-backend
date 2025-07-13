package eu.tealhelix.common.dao;

/**
 * The root of the DAO exception hierarchy.
 */
public abstract class DaoException extends RuntimeException {
	public DaoException() {
	}

	public DaoException(String message) {
		super(message);
	}

	public DaoException(String message, Throwable cause) {
		super(message, cause);
	}

	public DaoException(Throwable cause) {
		super(cause);
	}
}
