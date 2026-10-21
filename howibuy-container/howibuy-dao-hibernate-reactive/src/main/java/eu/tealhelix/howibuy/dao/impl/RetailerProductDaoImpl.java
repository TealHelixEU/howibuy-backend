package eu.tealhelix.howibuy.dao.impl;

import static jakarta.persistence.criteria.JoinType.LEFT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.catalogue.v1.model.Listing;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.howibuy.dao.RetailerProductDao;
import eu.tealhelix.howibuy.dao.jpa.ListingEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductEntity_;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductNameEntity;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RetailerProductDaoImpl implements RetailerProductDao {
	/**
	 * The external key of a Retailer Product, the only identity it shares with the Ingestion.
	 */
	private record ExternalKey(UUID retailerId, String productKey) {
		static ExternalKey of(ProductChange change) {
			return new ExternalKey(change.getRetailerId().asUuid(), change.getProductKey().asString());
		}

		static ExternalKey of(RetailerProductEntity product) {
			return new ExternalKey(product.getRetailerId(), product.getProductKey());
		}
	}

	/**
	 * The changes are applied to the products in memory, and the products created and those removed are handed to the
	 * session in one call each: a chain of a call per change would nest as deep as the changes are many, and overflow the
	 * stack of the event loop.
	 */
	@Override
	public Uni<Void> apply(ReactivePersistenceTxContext tx, List<ProductChange> changes) {
		if (changes.isEmpty()) return Uni.createFrom().voidItem();
		return findProducts(tx, changes).flatMap(products -> {
			var created = new ArrayList<RetailerProductEntity>();
			var removed = new ArrayList<RetailerProductEntity>();
			for (var change : changes) {
				apply(products, created, removed, change);
			}
			return tx.removeAll(removed.toArray()).chain(() -> tx.persistAll(created.toArray()));
		});
	}

	/**
	 * Read the products the changes are about, with their names and Listings.
	 *
	 * @return The products found, by external key; the products that do not exist yet are missing from it. It may hold
	 *         products no change is about, which are never looked up
	 */
	private Uni<Map<ExternalKey, RetailerProductEntity>> findProducts(ReactivePersistenceTxContext tx, List<ProductChange> changes) {
		var keys = changes.stream().map(ExternalKey::of).collect(Collectors.toSet());
		var cb = tx.getCriteriaBuilder();
		var q = cb.createQuery(RetailerProductEntity.class);
		var product = q.from(RetailerProductEntity.class);
		product.fetch(RetailerProductEntity_.names, LEFT);
		product.fetch(RetailerProductEntity_.listings, LEFT);
		q.select(product).distinct(true);
		// Matches every combination of the retailers and product keys, a few more products than asked for at most
		q.where(
				product.get(RetailerProductEntity_.retailerId).in(keys.stream().map(ExternalKey::retailerId).distinct().toList()),
				product.get(RetailerProductEntity_.productKey).in(keys.stream().map(ExternalKey::productKey).distinct().toList())
		);
		return tx.createQuery(q).getResultList().map(products -> products.stream()
				.collect(Collectors.toMap(ExternalKey::of, Function.identity(), (a, b) -> a, HashMap::new)));
	}

	/**
	 * Apply a change to the products in memory.
	 *
	 * @param products The products, by external key, which the change may add to or remove from
	 * @param created  The products to persist, which the change may add to or remove from
	 * @param removed  The stored products to remove, which the change may add to
	 */
	private static void apply(Map<ExternalKey, RetailerProductEntity> products, List<RetailerProductEntity> created, List<RetailerProductEntity> removed, ProductChange change) {
		var key = ExternalKey.of(change);
		var product = products.get(key);
		if (change.isRemoved()) {
			if (product != null) {
				products.remove(key);
				if (!created.remove(product)) {
					removed.add(product);
				}
			}
		} else if (product == null) {
			var newProduct = new RetailerProductEntity();
			newProduct.setId(UUID.randomUUID());
			newProduct.setRetailerId(key.retailerId());
			newProduct.setProductKey(key.productKey());
			copyState(change, newProduct);
			products.put(key, newProduct);
			created.add(newProduct);
		} else {
			copyState(change, product);
		}
	}

	/**
	 * Names and Listings are changed in place, rather than replaced by new rows, because Hibernate flushes the inserts
	 * before the deletes, and a new row would collide with the primary key of the one it replaces.
	 */
	private static void copyState(ProductChange change, RetailerProductEntity product) {
		product.setCharacteristics(change.getCharacteristics());
		product.setTags(change.getTags());
		product.setInputHash(change.getInputHash());
		product.setChangeSequence(change.getChangeSequence());

		product.getNames().keySet().retainAll(change.getNames().keySet());
		change.getNames().forEach((lang, name) -> product.getNames().computeIfAbsent(lang, l -> newName(product, l)).setName(name));

		var listings = change.getListings().stream().collect(Collectors.toMap(l -> l.getCatalogueId().asString(), Function.identity()));
		product.getListings().keySet().retainAll(listings.keySet());
		listings.forEach((catalogueId, listing) -> copyListing(listing, product.getListings().computeIfAbsent(catalogueId, c -> newListing(product, c))));
	}

	private static RetailerProductNameEntity newName(RetailerProductEntity product, String lang) {
		var name = new RetailerProductNameEntity();
		name.setRetailerProduct(product);
		name.setLang(lang);
		return name;
	}

	private static ListingEntity newListing(RetailerProductEntity product, String catalogueId) {
		var listing = new ListingEntity();
		listing.setRetailerProduct(product);
		listing.setCatalogueId(catalogueId);
		return listing;
	}

	private static void copyListing(Listing source, ListingEntity target) {
		target.setPrice(source.getPrice());
		target.setCurrency(source.getCurrency().getCurrencyCode());
		target.setActive(source.isActive());
	}
}
