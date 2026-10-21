package eu.tealhelix.cataloguesync;

/**
 * @param pages The pages applied to HowiBuy
 * @param products The products of those pages
 * @param cursor HowiBuy's cursor at the end of the sync
 */
public record SyncResult(int pages, long products, long cursor) {
}
