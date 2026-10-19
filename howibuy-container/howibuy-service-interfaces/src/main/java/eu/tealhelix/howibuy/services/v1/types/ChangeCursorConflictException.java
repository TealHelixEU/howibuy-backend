package eu.tealhelix.howibuy.services.v1.types;

/**
 * Thrown when a page of Product Changes does not start at the cursor, because another sync has moved it or the page was
 * read from a stale cursor. Nothing of the page is applied (mapped to HTTP 409).
 */
public class ChangeCursorConflictException extends RuntimeException {
	public ChangeCursorConflictException(long fromSequence) {
		super("The page does not start at the change cursor, fromSequence: " + fromSequence);
	}
}
