package eu.tealhelix.howibuy.dao.impl;

import java.util.List;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.howibuy.dao.ArchetypeCategoryDao;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity_;
import eu.tealhelix.howibuy.services.model.ArchetypeCategory;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeCategory;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class ArchetypeCategoryDaoImpl implements ArchetypeCategoryDao {
	private static final short L1_LEVEL = 1;

	@Override
	public Uni<List<ArchetypeCategory>> retrieveL1Categories(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var root = q.from(ArchetypeCategoryEntity.class);
		q.select(cb.tuple(root.get(ArchetypeCategoryEntity_.id), root.get(ArchetypeCategoryEntity_.name)))
				.where(cb.equal(root.get(ArchetypeCategoryEntity_.level), L1_LEVEL))
				.orderBy(cb.asc(root.get(ArchetypeCategoryEntity_.name)), cb.asc(root.get(ArchetypeCategoryEntity_.id)));
		return em.createQuery(q).getResultList().map(ArchetypeCategoryDaoImpl::toArchetypeCategories);
	}

	@Override
	public Uni<List<ArchetypeCategory>> retrieveSubcategories(ReactivePersistenceContext em, UUID parentId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var root = q.from(ArchetypeCategoryEntity.class);
		q.select(cb.tuple(root.get(ArchetypeCategoryEntity_.id), root.get(ArchetypeCategoryEntity_.name)))
				.where(cb.equal(root.get(ArchetypeCategoryEntity_.parent).get(ArchetypeCategoryEntity_.id), parentId))
				.orderBy(cb.asc(root.get(ArchetypeCategoryEntity_.name)), cb.asc(root.get(ArchetypeCategoryEntity_.id)));
		return em.createQuery(q).getResultList().map(ArchetypeCategoryDaoImpl::toArchetypeCategories);
	}

	private static List<ArchetypeCategory> toArchetypeCategories(List<Tuple> tuples) {
		return tuples.stream().map(ArchetypeCategoryDaoImpl::toArchetypeCategory).toList();
	}

	private static ArchetypeCategory toArchetypeCategory(Tuple tuple) {
		return ImmutableArchetypeCategory.builder()
				.id(tuple.get(0, UUID.class))
				.name(tuple.get(1, String.class))
				.build();
	}
}
