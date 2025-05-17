package eu.tealhelix.common.dao.reactive;

import io.smallrye.mutiny.Uni;

/**
 * A wrapper around the main runtime interface of a reactive persistence implementation that is also guaranteed to
 * run within a reactive transaction, in addition to the contract of the {@link ReactivePersistenceContext}.
 * <p>
 * The idea is that if a function requires a transaction it will declare an argument of this type,
 * just as it would be annotated with {@code @Transactional} in classic JPA.
 * <p>
 * In the case of Hibernate Reactive, this wraps a {@code Mutiny.Transaction} <em>and</em> a {@code Mutiny.Session}.
 */
public interface ReactivePersistenceTxContext extends ReactivePersistenceContext {
	Uni<Void> persist(Object entity);

	Uni<Void> persistAll(Object... entities);

	<T> Uni<T> merge(T entity);

	Uni<Void> remove(Object entity);

	Uni<Void> removeAll(Object... entities);

	boolean isMarkedForRollback();

	void markForRollback();
}
