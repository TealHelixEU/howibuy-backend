package eu.tealhelix.common.dao.reactive;

import java.util.List;
import java.util.Optional;

import io.smallrye.mutiny.Uni;

/**
 * A wrapper around the selection query object of a reactive persistence implementation.
 * <p>
 * In the case of Hibernate Reactive, this wraps a {@code Mutiny.SelectionQuery}.
 *
 * @param <R> The result type
 */
public interface ReactiveQuery<R> {
	Uni<List<R>> getResultList();
	Uni<R> getSingleResult();
	Uni<Optional<R>> getSingleOptionalResult();
	ReactiveQuery<R> setFirstResult(int firstResult);
	ReactiveQuery<R> setMaxResults(int maxResults);
	ReactiveQuery<R> setParameter(String parameter, Object argument);
}
