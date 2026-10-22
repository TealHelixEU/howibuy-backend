package eu.tealhelix.howibuy.dao.impl;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;
import static jakarta.persistence.criteria.JoinType.LEFT;

import static eu.tealhelix.common.utils.UniComprehensions.forcm;

import java.util.HashMap;
import java.util.LinkedHashSet;
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
import eu.tealhelix.howibuy.services.model.ImmutableProductToClassify;
import eu.tealhelix.howibuy.services.model.ProductToClassify;
import eu.tealhelix.howibuy.v1.model.Classification;
import eu.tealhelix.howibuy.v1.model.ClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.ImmutableClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.ImmutablePendingProduct;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
import eu.tealhelix.howibuy.v1.types.HasRetailerProductId;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.RetailerProductIdImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class ClassificationDaoImpl implements ClassificationDao {
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
				products -> findLanguages(em, products.stream().map(row -> row.get(id)).toList()),
				(products, languages) -> products.stream()
						.map(row -> toPendingProduct(row.get(id), row.get(inputHash), languages.getOrDefault(row.get(id), Set.of())))
						.toList()
		);
	}

	@Override
	public Uni<ProductToClassify> findLockedProductToClassify(ReactivePersistenceTxContext tx, HasRetailerProductId productId) {
		return tx.find(RetailerProductEntity.class, productId.getId().asUuid(), PESSIMISTIC_WRITE)
				.flatMap(product -> product == null ? Uni.createFrom().nullItem() : forcm(
						tx.find(RetailerEntity.class, product.getRetailerId()),
						retailer -> findLanguages(tx, List.of(product.getId())),
						(retailer, languages) -> ImmutableProductToClassify.builder()
								.inputHash(product.getInputHash())
								.languages(languages.getOrDefault(product.getId(), Set.of()))
								.classificationLanguage(retailer.getClassificationLanguage())
								.build()
				));
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
	 * @return The languages each product is named in, in the order of their codes
	 */
	private static Uni<Map<UUID, Set<String>>> findLanguages(ReactivePersistenceContext em, List<UUID> productIds) {
		if (productIds.isEmpty()) return Uni.createFrom().item(Map.of());
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var name = q.from(RetailerProductNameEntity.class);
		var productId = name.get(RetailerProductNameEntity_.retailerProduct).get(RetailerProductEntity_.id);
		var lang = name.get(RetailerProductNameEntity_.lang);
		q.select(cb.tuple(productId, lang));
		q.where(productId.in(productIds));
		q.orderBy(cb.asc(lang));
		return em.createQuery(q).getResultList().map(rows -> {
			var languages = new HashMap<UUID, Set<String>>();
			for (Tuple row : rows) {
				languages.computeIfAbsent(row.get(productId), p -> new LinkedHashSet<>()).add(row.get(lang));
			}
			return languages;
		});
	}

	private static void copy(Classification classification, ClassificationEntity entity) {
		entity.setArchetypeProductId(classification.getArchetypeProductId() != null ? classification.getArchetypeProductId().asUuid() : null);
		entity.setInputHash(classification.getInputHash());
		entity.setNeedsReview(classification.isNeedsReview());
		entity.setResolvedAt(classification.getResolvedAt());
	}

	private static ClassificationAttempt toClassificationAttempt(ClassificationAttemptEntity entity) {
		return ImmutableClassificationAttempt.builder()
				.language(entity.getLang())
				.classifier(entity.getClassifier())
				.classifierVersion(entity.getClassifierVersion())
				.archetypeProductId(entity.getArchetypeProductId() != null ? new ArchetypeProductIdImpl(entity.getArchetypeProductId().toString()) : null)
				.inputHash(entity.getInputHash())
				.createdAt(entity.getCreatedAt())
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
