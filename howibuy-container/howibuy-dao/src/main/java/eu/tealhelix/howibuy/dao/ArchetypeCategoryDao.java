package eu.tealhelix.howibuy.dao;

import java.util.List;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.howibuy.services.model.ArchetypeCategory;
import eu.tealhelix.howibuy.v1.types.HasArchetypeCategoryId;
import io.smallrye.mutiny.Uni;

public interface ArchetypeCategoryDao {
	/**
	 * The top-level (L1) categories of the SAFAD taxonomy.
	 */
	Uni<List<ArchetypeCategory>> retrieveL1Categories(ReactivePersistenceContext em);

	/**
	 * The direct children of the given category: the L2 subcategories of an L1 category, or the L3 subcategories of an
	 * L2 category. Scoping by parent id (rather than by category name) is required: subcategory names are unique only
	 * within a single parent, so the same name may appear under several parents across the taxonomy.
	 */
	Uni<List<ArchetypeCategory>> retrieveSubcategories(ReactivePersistenceContext em, HasArchetypeCategoryId parent);
}
