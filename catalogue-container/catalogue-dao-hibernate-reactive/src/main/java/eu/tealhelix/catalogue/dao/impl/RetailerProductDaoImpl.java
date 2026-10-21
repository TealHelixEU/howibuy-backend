package eu.tealhelix.catalogue.dao.impl;

import static jakarta.persistence.criteria.JoinType.LEFT;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.catalogue.dao.RetailerProductDao;
import eu.tealhelix.catalogue.dao.jpa.ListingEntity;
import eu.tealhelix.catalogue.dao.jpa.ListingEntity_;
import eu.tealhelix.catalogue.dao.jpa.RetailerProductEntity;
import eu.tealhelix.catalogue.dao.jpa.RetailerProductEntity_;
import eu.tealhelix.catalogue.dao.jpa.RetailerProductNameEntity;
import eu.tealhelix.catalogue.v1.model.ImmutableListing;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.Listing;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RetailerProductDaoImpl implements RetailerProductDao {
	@Override
	public Uni<Map<ProductKey, ProductChange>> findProducts(ReactivePersistenceContext em, RetailerId retailerId, Collection<ProductKey> productKeys) {
		return findEntities(em, retailerId.asUuid(), productKeys.stream().map(ProductKey::asString).toList())
				.map(products -> products.values().stream()
						.map(RetailerProductDaoImpl::toProductChange)
						.collect(Collectors.toMap(ProductChange::getProductKey, Function.identity())));
	}

	@Override
	public Uni<List<Long>> takeChangeSequences(ReactivePersistenceTxContext tx, int count) {
		if (count == 0) return Uni.createFrom().item(List.of());
		return tx.createNativeQuery("SELECT nextval('IN_CHANGE_SEQUENCE') FROM generate_series(1, :count)", Long.class)
				.setParameter("count", count)
				.getResultList()
				.map(values -> values.stream().sorted().toList());
	}

	@Override
	public Uni<Void> save(ReactivePersistenceTxContext tx, CatalogueId catalogueId, IngestionRunId runId, LocalDateTime seenAt, Collection<ProductChange> products) {
		if (products.isEmpty()) return Uni.createFrom().voidItem();
		var retailerId = products.iterator().next().getRetailerId().asUuid();
		return findEntities(tx, retailerId, products.stream().map(p -> p.getProductKey().asString()).toList()).flatMap(entities -> {
			Uni<Void> saved = Uni.createFrom().voidItem();
			for (var product : products) {
				var entity = entities.get(product.getProductKey().asString());
				if (entity == null) {
					var created = new RetailerProductEntity();
					created.setId(UUID.randomUUID());
					created.setRetailerId(retailerId);
					created.setProductKey(product.getProductKey().asString());
					copyState(product, created, catalogueId, runId, seenAt);
					entities.put(created.getProductKey(), created);
					saved = saved.chain(() -> tx.persist(created).replaceWithVoid());
				} else {
					copyState(product, entity, catalogueId, runId, seenAt);
				}
			}
			return saved;
		});
	}

	@Override
	public Uni<Long> countActiveListings(ReactivePersistenceContext em, CatalogueId catalogueId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(Long.class);
		var listing = q.from(ListingEntity.class);
		q.select(cb.count(listing.get(ListingEntity_.catalogueId)));
		q.where(cb.equal(listing.get(ListingEntity_.catalogueId), catalogueId.asString()), cb.isTrue(listing.get(ListingEntity_.active)));
		return em.createQuery(q).getSingleResult();
	}

	@Override
	public Uni<Long> countUnseenListings(ReactivePersistenceContext em, CatalogueId catalogueId, IngestionRunId runId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(Long.class);
		var listing = q.from(ListingEntity.class);
		q.select(cb.count(listing.get(ListingEntity_.catalogueId)));
		q.where(
				cb.equal(listing.get(ListingEntity_.catalogueId), catalogueId.asString()),
				cb.isTrue(listing.get(ListingEntity_.active)),
				cb.notEqual(listing.get(ListingEntity_.lastSeenRunId), runId.asUuid())
		);
		return em.createQuery(q).getSingleResult();
	}

	/**
	 * In SQL, because each product takes a value of the change sequence of its own, which a criteria update cannot
	 * express.
	 */
	@Override
	public Uni<Integer> deactivateUnseenListings(ReactivePersistenceTxContext tx, CatalogueId catalogueId, IngestionRunId runId) {
		return tx.createNativeUpdate("""
						WITH deactivated AS (
							UPDATE IN_LISTING SET active = FALSE
							WHERE catalogue_id = :catalogueId AND active AND last_seen_run_id <> :runId
							RETURNING retailer_product_id
						)
						UPDATE IN_RETAILER_PRODUCT p SET change_sequence = nextval('IN_CHANGE_SEQUENCE')
						FROM deactivated d WHERE p.id = d.retailer_product_id
						""")
				.setParameter("catalogueId", catalogueId.asString())
				.setParameter("runId", runId.asUuid())
				.execute();
	}

	/**
	 * In SQL, for the reason of {@link #deactivateUnseenListings(ReactivePersistenceTxContext, CatalogueId, IngestionRunId)}.
	 */
	@Override
	public Uni<Integer> deactivateListings(ReactivePersistenceTxContext tx, RetailerId retailerId, CatalogueId catalogueId, Collection<ProductKey> productKeys) {
		if (productKeys.isEmpty()) return Uni.createFrom().item(0);
		return tx.createNativeUpdate("""
						WITH deactivated AS (
							UPDATE IN_LISTING l SET active = FALSE
							FROM IN_RETAILER_PRODUCT p
							WHERE p.id = l.retailer_product_id AND p.retailer_id = :retailerId AND p.product_key IN (:productKeys)
								AND l.catalogue_id = :catalogueId AND l.active
							RETURNING l.retailer_product_id
						)
						UPDATE IN_RETAILER_PRODUCT p SET change_sequence = nextval('IN_CHANGE_SEQUENCE')
						FROM deactivated d WHERE p.id = d.retailer_product_id
						""")
				.setParameter("retailerId", retailerId.asUuid())
				.setParameter("productKeys", productKeys.stream().map(ProductKey::asString).toList())
				.setParameter("catalogueId", catalogueId.asString())
				.execute();
	}

	/**
	 * Read the products of a Retailer with their names and Listings.
	 *
	 * @return The products found, by product key, in a map that may be added to
	 */
	private Uni<Map<String, RetailerProductEntity>> findEntities(ReactivePersistenceContext em, UUID retailerId, List<String> productKeys) {
		if (productKeys.isEmpty()) return Uni.createFrom().item(new HashMap<>());
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RetailerProductEntity.class);
		var product = q.from(RetailerProductEntity.class);
		product.fetch(RetailerProductEntity_.names, LEFT);
		product.fetch(RetailerProductEntity_.listings, LEFT);
		q.select(product).distinct(true);
		q.where(
				cb.equal(product.get(RetailerProductEntity_.retailerId), retailerId),
				product.get(RetailerProductEntity_.productKey).in(productKeys)
		);
		return em.createQuery(q).getResultList().map(products -> products.stream()
				.collect(Collectors.toMap(RetailerProductEntity::getProductKey, Function.identity(), (a, b) -> a, HashMap::new)));
	}

	/**
	 * Names and Listings are changed in place, rather than replaced by new rows, because Hibernate flushes the inserts
	 * before the deletes, and a new row would collide with the primary key of the one it replaces.
	 */
	private static void copyState(ProductChange product, RetailerProductEntity entity, CatalogueId catalogueId, IngestionRunId runId, LocalDateTime seenAt) {
		entity.setCharacteristics(product.getCharacteristics());
		entity.setTags(product.getTags());
		entity.setInputHash(product.getInputHash());
		entity.setChangeSequence(product.getChangeSequence());
		entity.setRemoved(product.isRemoved());

		entity.getNames().keySet().retainAll(product.getNames().keySet());
		product.getNames().forEach((lang, name) -> entity.getNames().computeIfAbsent(lang, l -> newName(entity, l)).setName(name));

		var listings = product.getListings().stream().collect(Collectors.toMap(l -> l.getCatalogueId().asString(), Function.identity()));
		entity.getListings().keySet().retainAll(listings.keySet());
		listings.forEach((listingCatalogueId, listing) -> copyListing(listing, entity.getListings().computeIfAbsent(listingCatalogueId, c -> newListing(entity, c))));

		var seen = entity.getListings().get(catalogueId.asString());
		if (seen != null) {
			seen.setLastSeenAt(seenAt);
			seen.setLastSeenRunId(runId.asUuid());
		}
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

	/**
	 * The Listings are ordered by Catalogue, so that the same state always reads back the same.
	 */
	private static ProductChange toProductChange(RetailerProductEntity entity) {
		return ImmutableProductChange.builder()
				.retailerId(new GenericRetailerId(entity.getRetailerId().toString()))
				.productKey(new ProductKeyImpl(entity.getProductKey()))
				.changeSequence(entity.getChangeSequence())
				.isRemoved(entity.isRemoved())
				.inputHash(entity.getInputHash())
				.names(entity.getNames().values().stream().collect(Collectors.toMap(RetailerProductNameEntity::getLang, RetailerProductNameEntity::getName)))
				.characteristics(entity.getCharacteristics())
				.tags(entity.getTags())
				.listings(entity.getListings().values().stream()
						.sorted(Comparator.comparing(ListingEntity::getCatalogueId))
						.map(RetailerProductDaoImpl::toListing)
						.toList())
				.build();
	}

	private static Listing toListing(ListingEntity entity) {
		return ImmutableListing.builder()
				.catalogueId(new CatalogueIdImpl(entity.getCatalogueId()))
				.price(entity.getPrice())
				.currency(Currency.getInstance(entity.getCurrency()))
				.isActive(entity.isActive())
				.build();
	}
}
