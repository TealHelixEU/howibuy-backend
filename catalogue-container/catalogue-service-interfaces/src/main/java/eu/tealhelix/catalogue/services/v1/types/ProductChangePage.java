package eu.tealhelix.catalogue.services.v1.types;

import java.util.List;

import eu.tealhelix.catalogue.v1.model.ProductChange;

/**
 * A page of the Retailer Products that changed after a value of the change sequence.
 *
 * @param toSequence The value of the change sequence to read the next page after: that of the last product of the page,
 *                   or the value the page was read after if it is empty
 * @param products   The products, each in its complete current state, the lowest change sequence first
 */
public record ProductChangePage(long toSequence, List<ProductChange> products) {
}
