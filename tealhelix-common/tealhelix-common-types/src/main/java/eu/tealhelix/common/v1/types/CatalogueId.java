package eu.tealhelix.common.v1.types;

import eu.tealhelix.common.types.RepresentableAsString;

/**
 * Names one Catalogue, one of a Retailer's e-shops. It is a short stable code chosen by us, e.g. {@code rimi-lt},
 * identical in every service that knows the Catalogue, because retailers type it into their requests.
 */
public interface CatalogueId extends RepresentableAsString {
}
