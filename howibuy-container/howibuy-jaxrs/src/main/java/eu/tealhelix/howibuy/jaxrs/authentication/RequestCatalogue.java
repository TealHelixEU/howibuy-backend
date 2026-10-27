package eu.tealhelix.howibuy.jaxrs.authentication;

import java.util.Optional;
import jakarta.ws.rs.container.ContainerRequestContext;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;

/**
 * The Catalogue of the retailer that a request acts in, kept as a property of the request next to its security context.
 * It belongs to the session that the token of the request stands for, not to the user, so the user principal does not
 * carry it.
 */
public final class RequestCatalogue {
	private static final String PROPERTY = RequestCatalogue.class.getName();

	private RequestCatalogue() {
	}

	/**
	 * @param requestContext The request
	 * @param catalogueId    The Catalogue the request acts in, {@code null} if it acts in none
	 */
	public static void set(ContainerRequestContext requestContext, CatalogueId catalogueId) {
		requestContext.setProperty(PROPERTY, catalogueId);
	}

	/**
	 * @param requestContext The request
	 * @return The Catalogue the request acts in, empty if its token names none or it carries no token
	 */
	public static Optional<CatalogueId> currentCatalogue(ContainerRequestContext requestContext) {
		return Optional.ofNullable((CatalogueId) requestContext.getProperty(PROPERTY));
	}

	/**
	 * @param requestContext The request
	 * @return The Catalogue the request acts in
	 * @throws NotAuthorizedException If its token names none or it carries no token
	 */
	public static CatalogueId requireCatalogue(ContainerRequestContext requestContext) {
		return currentCatalogue(requestContext).orElseThrow(() -> new NotAuthorizedException("The request acts in no Catalogue"));
	}
}
