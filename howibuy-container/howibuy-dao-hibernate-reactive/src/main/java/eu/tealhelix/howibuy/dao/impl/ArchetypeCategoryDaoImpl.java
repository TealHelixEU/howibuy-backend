package eu.tealhelix.howibuy.dao.impl;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.howibuy.dao.ArchetypeCategoryDao;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity_;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class ArchetypeCategoryDaoImpl implements ArchetypeCategoryDao {
	private static final short L1_LEVEL = 1;

	@Override
	public Uni<List<String>> retrieveL1CategoryNames(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(String.class);
		var root = q.from(ArchetypeCategoryEntity.class);
		q.select(root.get(ArchetypeCategoryEntity_.name))
				.where(cb.equal(root.get(ArchetypeCategoryEntity_.level), L1_LEVEL));
		return em.createQuery(q).getResultList();
	}
}
