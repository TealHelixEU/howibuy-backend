package eu.tealhelix.howibuy.dao.impl;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;
import static jakarta.persistence.criteria.JoinType.LEFT;

import static eu.tealhelix.common.utils.UniComprehensions.forcm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.howibuy.dao.ClassificationDao;
import eu.tealhelix.howibuy.dao.jpa.ClassificationAttemptEntity;
import eu.tealhelix.howibuy.dao.jpa.ClassificationAttemptEntity_;
import eu.tealhelix.howibuy.dao.jpa.ClassificationEntity;
import eu.tealhelix.howibuy.dao.jpa.ClassificationEntity_;
import eu.tealhelix.howibuy.dao.jpa.RetailerEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductEntity_;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductNameEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductNameEntity_;
import eu.tealhelix.howibuy.services.model.ImmutableProductClassificationInput;
import eu.tealhelix.howibuy.services.model.ImmutableProductToClassify;
import eu.tealhelix.howibuy.services.model.ProductClassificationInput;
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
		q.select(cb.tuple(id, inputHash));
		q.where(cb.or(
				cb.isNull(classification.get(ClassificationEntity_.retailerProductId)),
				cb.notEqual(classification.get(ClassificationEntity_.inputHash), inputHash)
		));
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
				cb.equal(classification.get(ClassificationEntity_.inputHash), product.get(RetailerProductEntity_.inputHash))
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
		entity.setArchetypeProductId(classification.getArchetypeProductId() != null ? classification.getArchetypeProductId().asUuid() : null);
		entity.setInputHash(classification.getInputHash());
		entity.setNeedsReview(classification.isNeedsReview());
		entity.setResolvedAt(classification.getResolvedAt());
	}

	private static Classification toClassification(ClassificationEntity entity) {
		return ImmutableClassification.builder()
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

	private static PendingProduct toPendingProduct(UUID id, String inputHash, Set<String> languages) {
		return ImmutablePendingProduct.builder()
				.id(new RetailerProductIdImpl(id.toString()))
				.inputHash(inputHash)
				.languages(languages)
				.build();
	}
}
