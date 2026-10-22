package eu.tealhelix.common.dao.reactive;

import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaUpdate;

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
	/**
	 * Find an entity and lock its row until the transaction ends, e.g. so that the changes that depend on its state
	 * happen one after the other.
	 *
	 * @param entityClass The class of the entity
	 * @param id          The id of the entity
	 * @param lockMode    The lock, e.g. {@code PESSIMISTIC_WRITE} to wait for other transactions holding it
	 * @param <T>         The type of the entity
	 * @return The entity, {@code null} if there is none with this id
	 */
	<T> Uni<T> find(Class<T> entityClass, Object id, LockModeType lockMode);

	<T> Uni<T> persist(T entity);

	Uni<Void> persistAll(Object... entities);

	<T> Uni<T> merge(T entity);

	Uni<Void> remove(Object entity);

	Uni<Void> removeAll(Object... entities);

	boolean isMarkedForRollback();

	void markForRollback();

	<R> ReactiveUpdate createUpdate(CriteriaUpdate<R> criteriaUpdate);

	/**
	 * A delete changes the stored data just as an update does, and is issued and executed the same way, hence the
	 * {@link ReactiveUpdate}.
	 */
	<R> ReactiveUpdate createDelete(CriteriaDelete<R> criteriaDelete);

	/**
	 * An update in the SQL of the database, for the changes that a criteria update cannot express. The persistence
	 * context does not see what it changes: the entities it holds keep the state they had.
	 */
	ReactiveUpdate createNativeUpdate(String sql);
}
