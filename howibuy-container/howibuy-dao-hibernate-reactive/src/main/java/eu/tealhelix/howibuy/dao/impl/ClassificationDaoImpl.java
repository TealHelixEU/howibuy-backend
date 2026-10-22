package eu.tealhelix.howibuy.dao.impl;

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
import eu.tealhelix.howibuy.dao.ClassificationDao;
import eu.tealhelix.howibuy.dao.jpa.ClassificationEntity;
import eu.tealhelix.howibuy.dao.jpa.ClassificationEntity_;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductEntity_;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductNameEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerProductNameEntity_;
import eu.tealhelix.howibuy.v1.model.ImmutablePendingProduct;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
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

	private static PendingProduct toPendingProduct(UUID id, String inputHash, Set<String> languages) {
		return ImmutablePendingProduct.builder()
				.id(new RetailerProductIdImpl(id.toString()))
				.inputHash(inputHash)
				.languages(languages)
				.build();
	}
}
