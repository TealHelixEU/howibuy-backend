package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import java.util.List;

/**
 * What every request naming a basket carries: the products of the basket and the language to answer in.
 */
interface BasketRequest {
	String language();

	List<String> productKeys();
}
