package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.model.Listing;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.howibuy.dao.CatalogueChangeCursorDao;
import eu.tealhelix.howibuy.dao.CatalogueDao;
import eu.tealhelix.howibuy.dao.RetailerDao;
import eu.tealhelix.howibuy.dao.RetailerProductDao;
import eu.tealhelix.howibuy.services.v1.CatalogueChangeService;
import eu.tealhelix.howibuy.services.v1.types.ChangeCursorConflictException;
import eu.tealhelix.howibuy.services.v1.types.ProductChangePage;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class CatalogueChangeServiceImpl implements CatalogueChangeService {
	private final TealHelixAuthorization authorization;
	private final CatalogueChangeCursorDao cursorDao;
	private final CatalogueDao catalogueDao;
	private final RetailerDao retailerDao;
	private final RetailerProductDao retailerProductDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	@Inject
	public CatalogueChangeServiceImpl(
			TealHelixAuthorization authorization,
			CatalogueChangeCursorDao cursorDao,
			CatalogueDao catalogueDao,
			RetailerDao retailerDao,
			RetailerProductDao retailerProductDao,
			ReactivePersistenceContextFactory persistenceContextFactory
	) {
		this.authorization = authorization;
		this.cursorDao = cursorDao;
		this.catalogueDao = catalogueDao;
		this.retailerDao = retailerDao;
		this.retailerProductDao = retailerProductDao;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<Long> readCursor(User caller) {
		authorization.requireCatalogueSync(caller);
		return persistenceContextFactory.withoutTransaction(cursorDao::requireLastSequence);
	}

	@Override
	public Uni<Void> apply(User caller, ProductChangePage page) {
		authorization.requireCatalogueSync(caller);
		requireCompletePage(page);
		requireChangesWithinPage(page);
		return persistenceContextFactory.withTransaction(tx -> applyInTx(tx, page));
	}

	private static void requireCompletePage(ProductChangePage page) {
		RequiredInputMissingException.throwIfRequiredInputMissing("fromSequence", page.fromSequence());
		RequiredInputMissingException.throwIfRequiredInputMissing("toSequence", page.toSequence());
		RequiredInputMissingException.throwIfRequiredInputMissing("products", page.products());
	}

	private static void requireChangesWithinPage(ProductChangePage page) {
		if (page.toSequence() < page.fromSequence()) {
			throw BadInputValueException.fromInputNameAndHint("toSequence", "It must not be before fromSequence");
		}
		page.products().stream()
				.filter(change -> change.getChangeSequence() <= page.fromSequence() || change.getChangeSequence() > page.toSequence())
				.findFirst()
				.ifPresent(change -> {
					throw BadInputValueException.fromInputNameAndHint("products", "A change is outside the sequences of the page, changeSequence: " + change.getChangeSequence());
				});
	}

	/**
	 * The cursor is advanced first, so that a sync applying the same page at the same time waits here for this one to
	 * finish, and is then refused.
	 */
	private Uni<Void> applyInTx(ReactivePersistenceTxContext tx, ProductChangePage page) {
		return forc(
				cursorDao.advance(tx, page.fromSequence(), page.toSequence()),
				advanced -> advanced ? requireKnownRetailers(tx, page.products()) : Uni.createFrom().failure(new ChangeCursorConflictException(page.fromSequence())),
				retailersKnown -> requireCataloguesOfTheirRetailers(tx, page.products()),
				cataloguesKnown -> retailerProductDao.apply(tx, page.products())
		);
	}

	/**
	 * The Retailers are checked one at a time, because a persistence context runs one operation at a time. A page rarely
	 * has products of more than one.
	 */
	private Uni<Void> requireKnownRetailers(ReactivePersistenceContext em, List<ProductChange> changes) {
		Uni<Void> checked = Uni.createFrom().voidItem();
		for (var retailerId : changes.stream().map(ProductChange::getRetailerId).distinct().toList()) {
			checked = checked.chain(() -> retailerDao.findActiveFlag(em, retailerId)
					.invoke(active -> {
						if (active == null) {
							throw BadInputValueException.fromInputNameAndHint("retailerId", "No such Retailer, retailerId: " + retailerId.asString());
						}
					})
					.replaceWithVoid());
		}
		return checked;
	}

	private Uni<Void> requireCataloguesOfTheirRetailers(ReactivePersistenceContext em, List<ProductChange> changes) {
		var catalogueIds = changes.stream()
				.flatMap(change -> change.getListings().stream())
				.map(Listing::getCatalogueId)
				.collect(Collectors.toSet());
		return catalogueDao.findRetailers(em, catalogueIds)
				.invoke(retailersOfCatalogues -> changes.forEach(change -> requireCataloguesOfRetailer(retailersOfCatalogues, change)))
				.replaceWithVoid();
	}

	private static void requireCataloguesOfRetailer(Map<CatalogueId, RetailerId> retailersOfCatalogues, ProductChange change) {
		for (var listing : change.getListings()) {
			var retailerId = retailersOfCatalogues.get(listing.getCatalogueId());
			if (retailerId == null) {
				throw BadInputValueException.fromInputNameAndHint("catalogueId", "No such Catalogue, catalogueId: " + listing.getCatalogueId().asString());
			} else if (!retailerId.asUuid().equals(change.getRetailerId().asUuid())) {
				throw BadInputValueException.fromInputNameAndHint("catalogueId", "The Catalogue belongs to another Retailer, catalogueId: " + listing.getCatalogueId().asString());
			}
		}
	}
}
