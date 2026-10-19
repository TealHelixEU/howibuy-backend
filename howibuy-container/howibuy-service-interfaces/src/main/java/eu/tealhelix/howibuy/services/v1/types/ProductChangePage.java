package eu.tealhelix.howibuy.services.v1.types;

import java.util.List;

import eu.tealhelix.howibuy.v1.model.ProductChange;

/**
 * A page of the Product Changes read from the change cursor of the Ingestion.
 *
 * @param fromSequence The change sequence the page was read after, which must be the cursor it is applied on
 * @param toSequence   The change sequence the cursor continues from after the page
 * @param products     The changes, each with a change sequence after {@code fromSequence} and up to {@code toSequence}
 */
public record ProductChangePage(long fromSequence, long toSequence, List<ProductChange> products) {
}
