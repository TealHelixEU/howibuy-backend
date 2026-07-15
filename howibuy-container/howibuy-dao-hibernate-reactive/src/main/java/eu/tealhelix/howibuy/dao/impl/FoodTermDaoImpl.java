package eu.tealhelix.howibuy.dao.impl;

import java.util.List;
import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.Root;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.howibuy.dao.FoodTermDao;
import eu.tealhelix.howibuy.dao.jpa.FoodTermEntity;
import eu.tealhelix.howibuy.dao.jpa.FoodTermEntity_;
import eu.tealhelix.howibuy.services.model.FoodTerm;
import eu.tealhelix.howibuy.services.model.ImmutableFoodTerm;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class FoodTermDaoImpl implements FoodTermDao {
	@Override
	public Uni<List<FoodTerm>> retrieveByLanguage(ReactivePersistenceContext em, String language) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var root = q.from(FoodTermEntity.class);
		q.select(cb.tuple(
						root.get(FoodTermEntity_.term),
						root.get(FoodTermEntity_.canonicalEn),
						root.get(FoodTermEntity_.description),
						root.get(FoodTermEntity_.categoryHintL1),
						root.get(FoodTermEntity_.categoryHintL2),
						root.get(FoodTermEntity_.categoryHintL3)))
				.where(cb.equal(root.get(FoodTermEntity_.lang), language));
		return em.createQuery(q).getResultList().map(list -> toFoodTerms(list, root));
	}

	private static List<FoodTerm> toFoodTerms(List<Tuple> tuples, Root<FoodTermEntity> root) {
		return tuples.stream().map(t -> toFoodTerm(t, root)).toList();
	}

	private static FoodTerm toFoodTerm(Tuple tuple, Root<FoodTermEntity> root) {
		return ImmutableFoodTerm.builder()
				.term(tuple.get(root.get(FoodTermEntity_.term)))
				.canonicalEn(tuple.get(root.get(FoodTermEntity_.canonicalEn)))
				.description(tuple.get(root.get(FoodTermEntity_.description)))
				.categoryHintL1(hintLevel(tuple.get(root.get(FoodTermEntity_.categoryHintL1))))
				.categoryHintL2(hintLevel(tuple.get(root.get(FoodTermEntity_.categoryHintL2))))
				.categoryHintL3(hintLevel(tuple.get(root.get(FoodTermEntity_.categoryHintL3))))
				.build();
	}

	private static Optional<String> hintLevel(String value) {
		return Optional.ofNullable(value).filter(node -> !node.isBlank());
	}
}
