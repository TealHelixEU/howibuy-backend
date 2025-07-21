package eu.tealhelix.common.dao.reactive;

import java.util.function.Function;

import io.smallrye.mutiny.Uni;

/**
 * A factory for instances of {@link ReactivePersistenceContext}.
 */
public interface ReactivePersistenceContextFactory extends AutoCloseable {
	<T> Uni<T> withTransaction(Function<ReactivePersistenceTxContext, Uni<T>> work);

	<T> Uni<T> withoutTransaction(Function<ReactivePersistenceContext, Uni<T>> work);
}
