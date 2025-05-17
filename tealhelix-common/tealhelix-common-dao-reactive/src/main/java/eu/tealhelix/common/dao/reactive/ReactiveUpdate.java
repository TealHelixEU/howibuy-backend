package eu.tealhelix.common.dao.reactive;

import io.smallrye.mutiny.Uni;

/**
 * A wrapper around the update query object of a reactive persistence implementation.
 * <p>
 * In the case of Hibernate Reactive, this wraps a {@code Mutiny.MutationQuery}.
 */
public interface ReactiveUpdate {
	Uni<Integer> execute();
	ReactiveUpdate setParameter(String parameter, Object argument);
}
