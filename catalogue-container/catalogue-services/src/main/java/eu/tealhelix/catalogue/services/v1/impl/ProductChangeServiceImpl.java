package eu.tealhelix.catalogue.services.v1.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.dao.RetailerProductDao;
import eu.tealhelix.catalogue.services.v1.ProductChangeService;
import eu.tealhelix.catalogue.services.v1.types.ProductChangePage;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.v1.model.User;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ProductChangeServiceImpl implements ProductChangeService {
	public static final String DEFAULT_LIMIT_KEY = "catalogue.product-changes.default-limit";
	public static final String MAX_LIMIT_KEY = "catalogue.product-changes.max-limit";

	private final TealHelixAuthorization authorization;
	private final RetailerProductDao retailerProductDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final int defaultLimit;
	private final int maxLimit;

	@Inject
	public ProductChangeServiceImpl(
			TealHelixAuthorization authorization,
			RetailerProductDao retailerProductDao,
			ReactivePersistenceContextFactory persistenceContextFactory,
			@ConfigProperty(name = DEFAULT_LIMIT_KEY) int defaultLimit,
			@ConfigProperty(name = MAX_LIMIT_KEY) int maxLimit
	) {
		this.authorization = authorization;
		this.retailerProductDao = retailerProductDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.defaultLimit = defaultLimit;
		this.maxLimit = maxLimit;
	}

	@Override
	public Uni<ProductChangePage> findChanges(User caller, long after, @Nullable Integer limit) {
		authorization.requireCatalogueSync(caller);
		if (after < 0) {
			throw BadInputValueException.fromInputNameAndHint("after", "It must not be negative");
		}
		var pageSize = limit != null ? limit : defaultLimit;
		if (pageSize < 1 || pageSize > maxLimit) {
			throw BadInputValueException.fromInputNameAndHint("limit", "It must be from 1 to " + maxLimit);
		}
		return persistenceContextFactory.withoutTransaction(em -> retailerProductDao.findChangesAfter(em, after, pageSize))
				.map(products -> new ProductChangePage(products.isEmpty() ? after : products.getLast().getChangeSequence(), products));
	}
}
