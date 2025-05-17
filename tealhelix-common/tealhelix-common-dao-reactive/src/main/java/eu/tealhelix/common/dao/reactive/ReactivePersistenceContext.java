package eu.tealhelix.common.dao.reactive;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;

import io.smallrye.mutiny.Uni;

/**
 * A wrapper around the main runtime interface of a reactive persistence implementation, to keep the code
 * of the application independent of the implementation details.
 * <p>
 * In the case of Hibernate Reactive, this wraps a {@code Mutiny.Session}.
 */
public interface ReactivePersistenceContext {
	Uni<Void> flush();

	/**
	 * Convenience method for chaining, flushes normally just like {@link #flush()} and returns its argument.
	 *
	 * @param t   What to return
	 * @param <T> The type
	 * @return Its argument, {@code t}
	 */
	<T> Uni<T> flush(T t);

	<T> Uni<T> find(Class<T> entityClass, Object id);

	<T> T getReference(Class<T> entityClass, Object id);

	CriteriaBuilder getCriteriaBuilder();

	<R> ReactiveQuery<R> createQuery(CriteriaQuery<R> criteriaQuery);

	<R> ReactiveUpdate createUpdate(CriteriaUpdate<R> criteriaUpdate);
}
