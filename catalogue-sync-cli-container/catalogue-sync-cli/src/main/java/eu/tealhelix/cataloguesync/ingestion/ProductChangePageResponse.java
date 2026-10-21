package eu.tealhelix.cataloguesync.ingestion;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A page of the Product Changes of the Ingestion. The products are carried to HowiBuy as they were read, so the Catalogue
 * Sync reads none of their fields.
 *
 * @param toSequence The change sequence to read the next page after
 * @param products The products, the lowest change sequence first
 */
public record ProductChangePageResponse(long toSequence, List<JsonNode> products) {
}
