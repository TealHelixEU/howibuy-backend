package eu.tealhelix.howibuy.jaxrs.authentication.jwt;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.common.v1.model.User;

/**
 * What a validated token says about the requests made with it: who makes them, and the Catalogue they act in. The
 * Catalogue belongs to the session the token stands for, not to the user, so it is kept apart from the {@link User}.
 *
 * @param user        The user the requests are made as
 * @param catalogueId The Catalogue of the retailer that the requests act in, {@code null} unless the token is one that
 *                    the token exchange minted
 */
public record Authentication(User user, CatalogueId catalogueId) {
}
