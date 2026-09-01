package eu.tealhelix.howibuy.dao;

import java.util.List;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.howibuy.services.model.Substitutability;
import io.smallrye.mutiny.Uni;

public interface SubstitutabilityDao {
	/**
	 * Every substitutable pair of the WP3 matrix. The matrix is small, fixed reference data that the assessment reads
	 * as a whole and caches, so it is never queried pair by pair.
	 */
	Uni<List<Substitutability>> retrieveAll(ReactivePersistenceContext em);
}
