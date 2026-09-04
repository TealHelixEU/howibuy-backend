package eu.tealhelix.howibuy.dao.impl;

import java.util.List;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.howibuy.dao.SubstitutabilityDao;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeCategoryEntity_;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeSubstitutabilityEntity;
import eu.tealhelix.howibuy.dao.jpa.ArchetypeSubstitutabilityEntity_;
import eu.tealhelix.howibuy.services.model.ImmutableSubstitutability;
import eu.tealhelix.howibuy.services.model.Substitutability;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeCategoryIdImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class SubstitutabilityDaoImpl implements SubstitutabilityDao {
	@Override
	public Uni<List<Substitutability>> retrieveAll(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var root = q.from(ArchetypeSubstitutabilityEntity.class);
		var fromId = root.get(ArchetypeSubstitutabilityEntity_.fromCategory).get(ArchetypeCategoryEntity_.id);
		var toId = root.get(ArchetypeSubstitutabilityEntity_.toCategory).get(ArchetypeCategoryEntity_.id);
		q.select(cb.tuple(fromId, toId, root.get(ArchetypeSubstitutabilityEntity_.degree)))
				.orderBy(cb.asc(fromId), cb.asc(toId));
		return em.createQuery(q).getResultList().map(SubstitutabilityDaoImpl::toSubstitutabilities);
	}

	private static List<Substitutability> toSubstitutabilities(List<Tuple> tuples) {
		return tuples.stream().map(SubstitutabilityDaoImpl::toSubstitutability).toList();
	}

	private static Substitutability toSubstitutability(Tuple tuple) {
		return ImmutableSubstitutability.builder()
				.fromCategoryId(new ArchetypeCategoryIdImpl(tuple.get(0, UUID.class).toString()))
				.toCategoryId(new ArchetypeCategoryIdImpl(tuple.get(1, UUID.class).toString()))
				.degree(tuple.get(2, Short.class))
				.build();
	}
}
