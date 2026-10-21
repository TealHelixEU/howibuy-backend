package eu.tealhelix.cataloguesync.howibuy;

/**
 * @param lastSequence The change sequence of the last Product Change HowiBuy applied, 0 if it has applied none
 */
public record CatalogueChangeCursorResponse(long lastSequence) {
}
