package eu.tealhelix.sfc.v1.types;

/**
 * The lifecycle state of a user's compass attempt. An attempt starts {@link #IN_PROGRESS} — answers are freely
 * upsertable — and moves once, irreversibly, to {@link #COMPLETED} when the user explicitly locks it. Persisted by name
 * (as a string), not by ordinal, so the storage is stable against reordering.
 */
public enum AttemptStatus {
	IN_PROGRESS,
	COMPLETED
}
