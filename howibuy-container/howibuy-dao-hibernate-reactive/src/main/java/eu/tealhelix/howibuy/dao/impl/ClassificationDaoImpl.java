package eu.tealhelix.howibuy.dao.impl;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;
import static jakarta.persistence.criteria.JoinType.LEFT;

import static eu.tealhelix.common.utils.UniComprehensions.forcm;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.dao.ClassificationDao;
import eu.tealhelix.howibuy.dao.jpa.CatalogueEntity;
import eu.tealhelix.howibuy.dao.jpa.CatalogueEntity_;
import eu.tealhelix.howibuy.dao.jpa.ClassificationAttemptEntity;
import eu.tealhelix.howibuy.dao.jpa.ClassificationAttemptEntity_;
import eu.tealhelix.howibuy.dao.jpa.ClassificationEntity;
import eu.tealhelix.howibuy.dao.jpa.ClassificationEntity_;
import eu.tealhelix.howibuy.dao.jpa.ListingEntity;
import eu.tealhelix.howibuy.dao.jpa.ListingEntity_;
import eu.tealhelix.howibuy.dao.jpa.RetailerEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductEntity_;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductNameEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductNameEntity_;
import eu.tealhelix.howibuy.services.model.ArchetypeListing;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeListing;
import eu.tealhelix.howibuy.services.model.ImmutableProductClassificationInput;
import eu.tealhelix.howibuy.services.model.ImmutableProductToAssess;
import eu.tealhelix.howibuy.services.model.ImmutableProductToClassify;
import eu.tealhelix.howibuy.services.model.ProductClassificationInput;
import eu.tealhelix.howibuy.services.model.ProductToAssess;
import eu.tealhelix.howibuy.services.model.ProductToClassify;
import eu.tealhelix.howibuy.v1.model.Classification;
import eu.tealhelix.howibuy.v1.model.ClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.ClassificationToReview;
import eu.tealhelix.howibuy.v1.model.ImmutableClassification;
import eu.tealhelix.howibuy.v1.model.ImmutableClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.ImmutableClassificationToReview;
import eu.tealhelix.howibuy.v1.model.ImmutableMatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.ImmutablePendingProduct;
import eu.tealhelix.howibuy.v1.model.MatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.Classifier;
import eu.tealhelix.howibuy.v1.types.HasRetailerProductId;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.RetailerProductIdImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class ClassificationDaoImpl implements ClassificationDao {
	private static final String L1_CATEGORY = "l1Category";
	private static final String L2_CATEGORY = "l2Category";
	private static final String L3_CATEGORY = "l3Category";
	private static final String ARCHETYPE_PRODUCT = "archetypeProduct";

	/**
	 * The products are read first and their languages after them, rather than fetching the names with the products:
	 * Hibernate cannot limit the rows of a query that fetches a collection, and would read every pending product.
	 */
	@Override
	public Uni<List<PendingProduct>> findPending(ReactivePersistenceContext em, int limit) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var product = q.from(RetailerProductEntity.class);
		var classification = product.join(ClassificationEntity.class, LEFT);
		classification.on(cb.equal(classification.get(ClassificationEntity_.retailerProductId), product.get(RetailerProductEntity_.id)));
		var id = product.get(RetailerProductEntity_.id);
		var inputHash = product.get(RetailerProductEntity_.inputHash);
		var activeListing = q.subquery(String.class);
		var listing = activeListing.from(ListingEntity.class);
		activeListing.select(listing.get(ListingEntity_.catalogueId)).where(
				cb.equal(listing.get(ListingEntity_.retailerProduct), product),
				cb.isTrue(listing.get(ListingEntity_.active))
		);
		q.select(cb.tuple(id, inputHash));
		q.where(
				cb.exists(activeListing),
				cb.or(
						cb.isNull(classification.get(ClassificationEntity_.retailerProductId)),
						cb.notEqual(classification.get(ClassificationEntity_.inputHash), inputHash),
						cb.isTrue(classification.get(ClassificationEntity_.reopened))
				)
		);
		q.orderBy(cb.asc(product.get(RetailerProductEntity_.changeSequence)));
		return forcm(
				em.createQuery(q).setMaxResults(limit).getResultList(),
				products -> findNames(em, products.stream().map(row -> row.get(id)).toList()),
				(products, names) -> products.stream()
						.map(row -> toPendingProduct(row.get(id), row.get(inputHash), names.getOrDefault(row.get(id), Map.of()).keySet()))
						.toList()
		);
	}

	/**
	 * The attempts and the names are read after the Classifications, one query each for all the products.
	 */
	@Override
	public Uni<List<ClassificationToReview>> findToReview(ReactivePersistenceContext em, int limit) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(ClassificationEntity.class);
		var classification = q.from(ClassificationEntity.class);
		var product = classification.join(RetailerProductEntity.class);
		product.on(cb.equal(product.get(RetailerProductEntity_.id), classification.get(ClassificationEntity_.retailerProductId)));
		q.where(
				cb.isTrue(classification.get(ClassificationEntity_.needsReview)),
				cb.equal(classification.get(ClassificationEntity_.inputHash), product.get(RetailerProductEntity_.inputHash)),
				cb.isFalse(classification.get(ClassificationEntity_.reopened))
		);
		q.orderBy(cb.asc(classification.get(ClassificationEntity_.resolvedAt)), cb.asc(classification.get(ClassificationEntity_.retailerProductId)));
		return forcm(
				em.createQuery(q).setMaxResults(limit).getResultList(),
				classifications -> findAttemptsOfClassifications(em, classifications.stream().map(ClassificationEntity::getRetailerProductId).toList()),
				(classifications, attempts) -> findNames(em, classifications.stream().map(ClassificationEntity::getRetailerProductId).toList()),
				(classifications, attempts, names) -> classifications.stream()
						.map(entity -> toClassificationToReview(entity, names.getOrDefault(entity.getRetailerProductId(), Map.of()), attempts.getOrDefault(entity.getRetailerProductId(), List.of())))
						.toList()
		);
	}

	/**
	 * One query finds them all, joining the products to the Catalogue through their Retailer; their Listings are not
	 * read.
	 */
	@Override
	public Uni<List<ProductToAssess>> findProductsToAssess(ReactivePersistenceContext em, CatalogueId catalogueId, Set<ProductKey> productKeys) {
		if (productKeys.isEmpty()) return Uni.createFrom().item(List.of());
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var catalogue = q.from(CatalogueEntity.class);
		var product = catalogue.join(RetailerProductEntity.class);
		product.on(cb.equal(product.get(RetailerProductEntity_.retailerId), catalogue.get(CatalogueEntity_.retailerId)));
		var classification = product.join(ClassificationEntity.class, LEFT);
		classification.on(cb.equal(classification.get(ClassificationEntity_.retailerProductId), product.get(RetailerProductEntity_.id)));
		var id = product.get(RetailerProductEntity_.id);
		var productKey = product.get(RetailerProductEntity_.productKey);
		var inputHash = product.get(RetailerProductEntity_.inputHash);
		q.select(cb.tuple(id, productKey, inputHash, classification));
		q.where(
				cb.equal(catalogue.get(CatalogueEntity_.id), catalogueId.asString()),
				productKey.in(productKeys.stream().map(ProductKey::asString).toList())
		);
		return em.createQuery(q).getResultList().map(rows -> rows.stream()
				.map(row -> toProductToAssess(row.get(id), row.get(productKey), row.get(inputHash), row.get(classification)))
				.toList());
	}

	@Override
	public Uni<Set<ArchetypeProductId>> findStockedArchetypes(ReactivePersistenceContext em, CatalogueId catalogueId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(UUID.class);
		var listing = q.from(ListingEntity.class);
		var classification = listing.join(ClassificationEntity.class);
		classification.on(cb.equal(classification.get(ClassificationEntity_.retailerProductId), listing.get(ListingEntity_.retailerProduct).get(RetailerProductEntity_.id)));
		var archetypeProductId = classification.get(ClassificationEntity_.archetypeProductId);
		q.select(archetypeProductId).distinct(true);
		q.where(
				cb.equal(listing.get(ListingEntity_.catalogueId), catalogueId.asString()),
				cb.isTrue(listing.get(ListingEntity_.active)),
				cb.isNotNull(archetypeProductId)
		);
		return em.createQuery(q).getResultList().map(ids -> ids.stream()
				.map(id -> new ArchetypeProductIdImpl(id.toString()))
				.collect(toUnmodifiableSet()));
	}

	/**
	 * One query finds them all, joining each Listing to the product's name in the language asked for and to its name in
	 * the Catalogue's primary language, either of which may be missing.
	 */
	@Override
	public Uni<List<ArchetypeListing>> findListingsOfArchetypes(ReactivePersistenceContext em, CatalogueId catalogueId, Set<ArchetypeProductId> archetypeIds, String language) {
		if (archetypeIds.isEmpty()) return Uni.createFrom().item(List.of());
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var listing = q.from(ListingEntity.class);
		var productId = listing.get(ListingEntity_.retailerProduct).get(RetailerProductEntity_.id);
		var product = listing.join(ListingEntity_.retailerProduct);
		var catalogue = listing.join(CatalogueEntity.class);
		catalogue.on(cb.equal(catalogue.get(CatalogueEntity_.id), listing.get(ListingEntity_.catalogueId)));
		var classification = listing.join(ClassificationEntity.class);
		classification.on(cb.equal(classification.get(ClassificationEntity_.retailerProductId), productId));
		var nameAskedFor = listing.join(RetailerProductNameEntity.class, LEFT);
		nameAskedFor.on(
				cb.equal(nameAskedFor.get(RetailerProductNameEntity_.retailerProduct).get(RetailerProductEntity_.id), productId),
				cb.equal(nameAskedFor.get(RetailerProductNameEntity_.lang), language));
		var primaryLanguage = catalogue.get(CatalogueEntity_.primaryLanguage);
		var primaryName = listing.join(RetailerProductNameEntity.class, LEFT);
		primaryName.on(
				cb.equal(primaryName.get(RetailerProductNameEntity_.retailerProduct).get(RetailerProductEntity_.id), productId),
				cb.equal(primaryName.get(RetailerProductNameEntity_.lang), primaryLanguage));
		var archetypeProductId = classification.get(ClassificationEntity_.archetypeProductId);
		var productKey = product.get(RetailerProductEntity_.productKey);
		var price = listing.get(ListingEntity_.price);
		var currency = listing.get(ListingEntity_.currency);
		var nameInLanguageAskedFor = nameAskedFor.get(RetailerProductNameEntity_.name);
		var nameInPrimaryLanguage = primaryName.get(RetailerProductNameEntity_.name);
		q.select(cb.tuple(archetypeProductId, productId, productKey, price, currency, nameInLanguageAskedFor, nameInPrimaryLanguage, primaryLanguage));
		q.where(
				cb.equal(listing.get(ListingEntity_.catalogueId), catalogueId.asString()),
				cb.isTrue(listing.get(ListingEntity_.active)),
				archetypeProductId.in(archetypeIds.stream().map(ArchetypeProductId::asUuid).toList())
		);
		return em.createQuery(q).getResultList().map(rows -> rows.stream()
				.map(row -> toArchetypeListing(
						row.get(archetypeProductId), row.get(productId), row.get(productKey), row.get(price), row.get(currency),
						language, row.get(nameInLanguageAskedFor), row.get(primaryLanguage), row.get(nameInPrimaryLanguage)))
				.toList());
	}

	@Override
	public Uni<ProductToClassify> findLockedProductToClassify(ReactivePersistenceTxContext tx, HasRetailerProductId productId) {
		return tx.find(RetailerProductEntity.class, productId.getId().asUuid(), PESSIMISTIC_WRITE)
				.flatMap(product -> product == null ? Uni.createFrom().nullItem() : forcm(
						tx.find(RetailerEntity.class, product.getRetailerId()),
						retailer -> findNames(tx, List.of(product.getId())),
						(retailer, names) -> ImmutableProductToClassify.builder()
								.inputHash(product.getInputHash())
								.languages(names.getOrDefault(product.getId(), Map.of()).keySet())
								.classificationLanguage(retailer.getClassificationLanguage())
								.build()
				));
	}

	@Override
	public Uni<ProductClassificationInput> findClassificationInput(ReactivePersistenceContext em, HasRetailerProductId productId) {
		return em.find(RetailerProductEntity.class, productId.getId().asUuid())
				.flatMap(product -> product == null ? Uni.createFrom().nullItem() : findNames(em, List.of(product.getId()))
						.map(names -> ImmutableProductClassificationInput.builder()
								.inputHash(product.getInputHash())
								.names(names.getOrDefault(product.getId(), Map.of()))
								.characteristics(product.getCharacteristics())
								.tags(product.getTags())
								.build()));
	}

	@Override
	public Uni<Void> recordAttempt(ReactivePersistenceTxContext tx, HasRetailerProductId productId, ClassificationAttempt attempt) {
		var entity = new ClassificationAttemptEntity();
		entity.setId(UUID.randomUUID());
		entity.setRetailerProductId(productId.getId().asUuid());
		entity.setLang(attempt.getLanguage());
		entity.setClassifier(attempt.getClassifier());
		entity.setClassifierVersion(attempt.getClassifierVersion());
		entity.setArchetypeProductId(attempt.getArchetypeProductId() != null ? attempt.getArchetypeProductId().asUuid() : null);
		entity.setInputHash(attempt.getInputHash());
		entity.setCreatedAt(attempt.getCreatedAt());
		entity.setDiagnostics(attempt.getDiagnostics() != null ? toMap(attempt.getDiagnostics()) : null);
		return tx.persist(entity).replaceWithVoid();
	}

	@Override
	public Uni<List<ClassificationAttempt>> findAttempts(ReactivePersistenceContext em, HasRetailerProductId productId, String inputHash) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(ClassificationAttemptEntity.class);
		var attempt = q.from(ClassificationAttemptEntity.class);
		q.where(
				cb.equal(attempt.get(ClassificationAttemptEntity_.retailerProductId), productId.getId().asUuid()),
				cb.equal(attempt.get(ClassificationAttemptEntity_.inputHash), inputHash)
		);
		return em.createQuery(q).getResultList().map(attempts -> attempts.stream().map(ClassificationDaoImpl::toClassificationAttempt).toList());
	}

	@Override
	public Uni<Void> saveClassification(ReactivePersistenceTxContext tx, HasRetailerProductId productId, Classification classification) {
		return tx.find(ClassificationEntity.class, productId.getId().asUuid()).flatMap(stored -> {
			if (stored != null) {
				copy(classification, stored);
				return Uni.createFrom().voidItem();
			}
			var entity = new ClassificationEntity();
			entity.setRetailerProductId(productId.getId().asUuid());
			copy(classification, entity);
			return tx.persist(entity).replaceWithVoid();
		});
	}

	/**
	 * One statement reopens them all: the Classifications of the classifier with an attempt of the version that no later
	 * attempt of the classifier in the same language, made from the same input hash, replaced.
	 */
	@Override
	public Uni<Integer> reopen(ReactivePersistenceTxContext tx, Classifier classifier, String classifierVersion) {
		var cb = tx.getCriteriaBuilder();
		var u = cb.createCriteriaUpdate(ClassificationEntity.class);
		var classification = u.from(ClassificationEntity.class);
		var attemptOfVersion = u.subquery(UUID.class);
		var attempt = attemptOfVersion.from(ClassificationAttemptEntity.class);
		var laterAttempt = attemptOfVersion.subquery(UUID.class);
		var later = laterAttempt.from(ClassificationAttemptEntity.class);
		laterAttempt.select(later.get(ClassificationAttemptEntity_.id)).where(
				cb.equal(later.get(ClassificationAttemptEntity_.retailerProductId), attempt.get(ClassificationAttemptEntity_.retailerProductId)),
				cb.equal(later.get(ClassificationAttemptEntity_.inputHash), attempt.get(ClassificationAttemptEntity_.inputHash)),
				cb.equal(later.get(ClassificationAttemptEntity_.classifier), attempt.get(ClassificationAttemptEntity_.classifier)),
				cb.equal(later.get(ClassificationAttemptEntity_.lang), attempt.get(ClassificationAttemptEntity_.lang)),
				cb.greaterThan(later.get(ClassificationAttemptEntity_.createdAt), attempt.get(ClassificationAttemptEntity_.createdAt))
		);
		attemptOfVersion.select(attempt.get(ClassificationAttemptEntity_.id)).where(
				cb.equal(attempt.get(ClassificationAttemptEntity_.retailerProductId), classification.get(ClassificationEntity_.retailerProductId)),
				cb.equal(attempt.get(ClassificationAttemptEntity_.inputHash), classification.get(ClassificationEntity_.inputHash)),
				cb.equal(attempt.get(ClassificationAttemptEntity_.classifier), classifier),
				cb.equal(attempt.get(ClassificationAttemptEntity_.classifierVersion), classifierVersion),
				cb.not(cb.exists(laterAttempt))
		);
		u.set(classification.get(ClassificationEntity_.reopened), true);
		u.where(
				cb.equal(classification.get(ClassificationEntity_.classifier), classifier),
				cb.exists(attemptOfVersion)
		);
		return tx.createUpdate(u).execute();
	}

	/**
	 * @return The names of each product by their language, in the order of the language codes
	 */
	private static Uni<Map<UUID, Map<String, String>>> findNames(ReactivePersistenceContext em, List<UUID> productIds) {
		if (productIds.isEmpty()) return Uni.createFrom().item(Map.of());
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var name = q.from(RetailerProductNameEntity.class);
		var productId = name.get(RetailerProductNameEntity_.retailerProduct).get(RetailerProductEntity_.id);
		var lang = name.get(RetailerProductNameEntity_.lang);
		var text = name.get(RetailerProductNameEntity_.name);
		q.select(cb.tuple(productId, lang, text));
		q.where(productId.in(productIds));
		q.orderBy(cb.asc(lang));
		return em.createQuery(q).getResultList().map(rows -> {
			var names = new HashMap<UUID, Map<String, String>>();
			for (Tuple row : rows) {
				names.computeIfAbsent(row.get(productId), p -> new LinkedHashMap<>()).put(row.get(lang), row.get(text));
			}
			return names;
		});
	}

	/**
	 * @return The attempts of each product made from the input hash of its Classification, the latest first
	 */
	private static Uni<Map<UUID, List<ClassificationAttempt>>> findAttemptsOfClassifications(ReactivePersistenceContext em, List<UUID> productIds) {
		if (productIds.isEmpty()) return Uni.createFrom().item(Map.of());
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(ClassificationAttemptEntity.class);
		var attempt = q.from(ClassificationAttemptEntity.class);
		var classification = attempt.join(ClassificationEntity.class);
		classification.on(
				cb.equal(classification.get(ClassificationEntity_.retailerProductId), attempt.get(ClassificationAttemptEntity_.retailerProductId)),
				cb.equal(classification.get(ClassificationEntity_.inputHash), attempt.get(ClassificationAttemptEntity_.inputHash))
		);
		q.where(attempt.get(ClassificationAttemptEntity_.retailerProductId).in(productIds));
		q.orderBy(cb.desc(attempt.get(ClassificationAttemptEntity_.createdAt)));
		return em.createQuery(q).getResultList().map(entities -> {
			var attempts = new HashMap<UUID, List<ClassificationAttempt>>();
			for (var entity : entities) {
				attempts.computeIfAbsent(entity.getRetailerProductId(), p -> new ArrayList<>()).add(toClassificationAttempt(entity));
			}
			return attempts;
		});
	}

	private static void copy(Classification classification, ClassificationEntity entity) {
		entity.setClassifier(classification.getClassifier());
		entity.setArchetypeProductId(classification.getArchetypeProductId() != null ? classification.getArchetypeProductId().asUuid() : null);
		entity.setInputHash(classification.getInputHash());
		entity.setNeedsReview(classification.isNeedsReview());
		entity.setResolvedAt(classification.getResolvedAt());
		entity.setReopened(false);
	}

	private static Classification toClassification(ClassificationEntity entity) {
		return ImmutableClassification.builder()
				.classifier(entity.getClassifier())
				.archetypeProductId(entity.getArchetypeProductId() != null ? new ArchetypeProductIdImpl(entity.getArchetypeProductId().toString()) : null)
				.inputHash(entity.getInputHash())
				.isNeedsReview(entity.isNeedsReview())
				.resolvedAt(entity.getResolvedAt())
				.build();
	}

	private static ClassificationAttempt toClassificationAttempt(ClassificationAttemptEntity entity) {
		return ImmutableClassificationAttempt.builder()
				.language(entity.getLang())
				.classifier(entity.getClassifier())
				.classifierVersion(entity.getClassifierVersion())
				.archetypeProductId(entity.getArchetypeProductId() != null ? new ArchetypeProductIdImpl(entity.getArchetypeProductId().toString()) : null)
				.inputHash(entity.getInputHash())
				.createdAt(entity.getCreatedAt())
				.diagnostics(entity.getDiagnostics() != null ? toMatchDiagnostics(entity.getDiagnostics()) : null)
				.build();
	}

	/**
	 * @return The names of the levels the matching reached, by the name of their level
	 */
	private static Map<String, String> toMap(MatchDiagnostics diagnostics) {
		var levels = new LinkedHashMap<String, String>();
		putIfReached(levels, L1_CATEGORY, diagnostics.getL1Category());
		putIfReached(levels, L2_CATEGORY, diagnostics.getL2Category());
		putIfReached(levels, L3_CATEGORY, diagnostics.getL3Category());
		putIfReached(levels, ARCHETYPE_PRODUCT, diagnostics.getArchetypeProduct());
		return levels;
	}

	private static void putIfReached(Map<String, String> levels, String level, String name) {
		if (name != null) levels.put(level, name);
	}

	private static MatchDiagnostics toMatchDiagnostics(Map<String, String> levels) {
		return ImmutableMatchDiagnostics.builder()
				.l1Category(levels.get(L1_CATEGORY))
				.l2Category(levels.get(L2_CATEGORY))
				.l3Category(levels.get(L3_CATEGORY))
				.archetypeProduct(levels.get(ARCHETYPE_PRODUCT))
				.build();
	}

	private static ClassificationToReview toClassificationToReview(ClassificationEntity entity, Map<String, String> names, List<ClassificationAttempt> attempts) {
		return ImmutableClassificationToReview.builder()
				.id(new RetailerProductIdImpl(entity.getRetailerProductId().toString()))
				.names(names)
				.classification(toClassification(entity))
				.attempts(attempts)
				.build();
	}

	private static ArchetypeListing toArchetypeListing(
			UUID archetypeProductId, UUID productId, String productKey, BigDecimal price, String currency,
			String language, @Nullable String nameInLanguage, String primaryLanguage, @Nullable String nameInPrimaryLanguage) {
		var names = new HashMap<String, String>();
		if (nameInPrimaryLanguage != null) names.put(primaryLanguage, nameInPrimaryLanguage);
		if (nameInLanguage != null) names.put(language, nameInLanguage);
		return ImmutableArchetypeListing.builder()
				.id(new RetailerProductIdImpl(productId.toString()))
				.productKey(new ProductKeyImpl(productKey))
				.archetypeProductId(new ArchetypeProductIdImpl(archetypeProductId.toString()))
				.price(price)
				.currency(Currency.getInstance(currency))
				.names(names)
				.primaryLanguage(primaryLanguage)
				.build();
	}

	/**
	 * The product is pending by the rule of {@link #findPending}, whether a Catalogue offers it or not.
	 */
	private static ProductToAssess toProductToAssess(UUID id, String productKey, String inputHash, ClassificationEntity classification) {
		return ImmutableProductToAssess.builder()
				.id(new RetailerProductIdImpl(id.toString()))
				.productKey(new ProductKeyImpl(productKey))
				.classification(classification != null ? toClassification(classification) : null)
				.isPending(classification == null || !classification.getInputHash().equals(inputHash) || classification.isReopened())
				.build();
	}

	private static PendingProduct toPendingProduct(UUID id, String inputHash, Set<String> languages) {
		return ImmutablePendingProduct.builder()
				.id(new RetailerProductIdImpl(id.toString()))
				.inputHash(inputHash)
				.languages(languages)
				.build();
	}
}
