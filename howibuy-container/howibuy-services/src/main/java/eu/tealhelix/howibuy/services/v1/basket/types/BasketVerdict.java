package eu.tealhelix.howibuy.services.v1.basket.types;

import java.util.List;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.BasketMatch;

/**
 * What the rules say about a basket.
 *
 * @param basketMatch            the Basket Match, {@code null} when the basket has no scored product
 * @param xpValue                the XP the basket earns
 * @param problematicProductKeys the Problematic Products, lowest scientific Overall Score first
 */
public record BasketVerdict(@Nullable BasketMatch basketMatch, int xpValue, List<ProductKey> problematicProductKeys) {
}
