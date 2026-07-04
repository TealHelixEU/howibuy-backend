package eu.tealhelix.howibuy.dao;

import java.util.List;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import io.smallrye.mutiny.Uni;

public interface ArchetypeCategoryDao {
	/**
	 * The names of the top-level (L1) categories of the SAFAD taxonomy.
	 */
	Uni<List<String>> retrieveL1CategoryNames(ReactivePersistenceContext em);
}
