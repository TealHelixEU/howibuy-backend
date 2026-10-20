package eu.tealhelix.catalogue.services.v1.impl;

import java.util.function.Supplier;

import io.smallrye.mutiny.Uni;

/**
 * Runs a reactive call from code that is allowed to block, and waits for its result. Reactive persistence must run on
 * the event loop, which blocking code is not on, so the call is made there and only its result comes back.
 */
public interface UniAwaiter {
	/**
	 * Make a reactive call and wait for its result.
	 *
	 * @param call Makes the call
	 * @param <T>  The type of the result
	 * @return The result; a failure of the call is thrown
	 */
	<T> T await(Supplier<Uni<T>> call);
}
