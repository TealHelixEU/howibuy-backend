package eu.tealhelix.howibuy.services.v1.types;

import java.util.List;

import eu.tealhelix.catalogue.v1.model.ProductChange;

/**
 * A page of the Product Changes read from the change cursor of the Ingestion. Its parts may be {@code null} as a request
 * carried them; {@link eu.tealhelix.howibuy.services.v1.CatalogueChangeService#apply} requires every one.
 *
 * @param fromSequence The change sequence the page was read after, which must be the cursor it is applied on
 * @param toSequence   The change sequence the cursor continues from after the page
 * @param products     The changes, each with a change sequence after {@code fromSequence} and up to {@code toSequence}
 */
public record ProductChangePage(Long fromSequence, Long toSequence, List<ProductChange> products) {
}
