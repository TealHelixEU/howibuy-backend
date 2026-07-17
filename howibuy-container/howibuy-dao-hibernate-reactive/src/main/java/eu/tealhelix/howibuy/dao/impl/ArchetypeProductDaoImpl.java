package eu.tealhelix.howibuy.dao.impl;

import java.util.List;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.howibuy.dao.ArchetypeProductDao;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity_;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeProductEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeProductEntity_;
import eu.tealhelix.howibuy.services.model.ArchetypeProduct;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeProduct;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class ArchetypeProductDaoImpl implements ArchetypeProductDao {
	@Override
	public Uni<List<ArchetypeProduct>> retrieveProductsInCategory(ReactivePersistenceContext em, UUID categoryId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var root = q.from(ArchetypeProductEntity.class);
		q.select(cb.tuple(root.get(ArchetypeProductEntity_.id), root.get(ArchetypeProductEntity_.name)))
				.where(cb.equal(root.get(ArchetypeProductEntity_.category).get(ArchetypeCategoryEntity_.id), categoryId))
				.orderBy(cb.asc(root.get(ArchetypeProductEntity_.name)), cb.asc(root.get(ArchetypeProductEntity_.id)));
		return em.createQuery(q).getResultList().map(ArchetypeProductDaoImpl::toArchetypeProducts);
	}

	private static List<ArchetypeProduct> toArchetypeProducts(List<Tuple> tuples) {
		return tuples.stream().map(ArchetypeProductDaoImpl::toArchetypeProduct).toList();
	}

	private static ArchetypeProduct toArchetypeProduct(Tuple tuple) {
		return ImmutableArchetypeProduct.builder()
				.id(tuple.get(0, UUID.class))
				.name(tuple.get(1, String.class))
				.build();
	}
}
