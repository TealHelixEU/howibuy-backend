package eu.tealhelix.cataloguesync.howibuy;

/**
 * HowiBuy refused a page that does not start at its cursor: another sync moved the cursor after this one read it.
 */
public class CursorConflictException extends RuntimeException {
	public CursorConflictException(String message) {
		super(message);
	}
}
