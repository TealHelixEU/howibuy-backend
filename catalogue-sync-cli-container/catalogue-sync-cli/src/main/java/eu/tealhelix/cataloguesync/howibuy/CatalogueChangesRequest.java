package eu.tealhelix.cataloguesync.howibuy;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A page of Product Changes for HowiBuy to apply, the products as the Ingestion gave them.
 *
 * @param fromSequence The change sequence the page was read after, which must be HowiBuy's cursor
 * @param toSequence The change sequence HowiBuy's cursor moves to once the page is applied
 * @param products The products, the lowest change sequence first
 */
public record CatalogueChangesRequest(long fromSequence, long toSequence, List<JsonNode> products) {
}
