package eu.tealhelix.howibuy.v1.types;

import java.util.UUID;

import eu.tealhelix.common.types.RepresentableAsString;

/**
 * Names one Retailer Product in HowiBuy. HowiBuy assigns it; the Ingestion knows the same product by an id of its own,
 * and the two share only the product's external key, the Retailer and its product key.
 */
public interface RetailerProductId extends HasRetailerProductId, RepresentableAsString {
	@Override
	default RetailerProductId getId() {
		return this;
	}

	UUID asUuid();
}
