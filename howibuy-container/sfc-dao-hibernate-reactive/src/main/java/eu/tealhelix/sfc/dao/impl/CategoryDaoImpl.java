package eu.tealhelix.sfc.dao.impl;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.Path;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.sfc.dao.CategoryDao;
import eu.tealhelix.sfc.dao.jpa.CategoryEntity;
import eu.tealhelix.sfc.dao.jpa.CategoryEntity_;
import eu.tealhelix.sfc.dao.jpa.CategoryTextEntity;
import eu.tealhelix.sfc.dao.jpa.CategoryTextEntity_;
import eu.tealhelix.sfc.v1.model.Category;
import eu.tealhelix.sfc.v1.model.ImmutableCategory;
import eu.tealhelix.sfc.v1.types.impl.CategoryIdImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class CategoryDaoImpl implements CategoryDao {
	@Override
	public Uni<List<Category>> retrieveByLanguage(ReactivePersistenceContext em, String language) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var root = q.from(CategoryTextEntity.class);
		var category = root.get(CategoryTextEntity_.category);
		q.select(cb.tuple(
						category.get(CategoryEntity_.id),
						category.get(CategoryEntity_.dimension),
						root.get(CategoryTextEntity_.name),
						root.get(CategoryTextEntity_.description),
						root.get(CategoryTextEntity_.videoUrl),
						root.get(CategoryTextEntity_.detailUrl)))
				.where(cb.equal(root.get(CategoryTextEntity_.lang), language))
				.orderBy(cb.asc(category.get(CategoryEntity_.dimension)), cb.asc(category.get(CategoryEntity_.id)));
		return em.createQuery(q).getResultList().map(list -> toCategories(category, root, list));
	}

	private static List<Category> toCategories(Path<CategoryEntity> category, Path<CategoryTextEntity> text, List<Tuple> tuples) {
		return tuples.stream().map(t -> toCategory(category, text, t)).toList();
	}

	private static Category toCategory(Path<CategoryEntity> category, Path<CategoryTextEntity> text, Tuple tuple) {
		return ImmutableCategory.builder()
				.id(new CategoryIdImpl(tuple.get(category.get(CategoryEntity_.id)).toString()))
				.dimension(tuple.get(category.get(CategoryEntity_.dimension)))
				.name(tuple.get(text.get(CategoryTextEntity_.name)))
				.description(tuple.get(text.get(CategoryTextEntity_.description)))
				.videoUrl(tuple.get(text.get(CategoryTextEntity_.videoUrl)))
				.detailUrl(tuple.get(text.get(CategoryTextEntity_.detailUrl)))
				.build();
	}
}
