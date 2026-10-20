package eu.tealhelix.catalogue;

import java.util.function.Supplier;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.catalogue.services.v1.impl.UniAwaiter;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;

/**
 * Runs the reactive call on a Vert.x duplicated context, as Hibernate Reactive requires, and blocks the calling worker
 * thread until it completes.
 */
@ApplicationScoped
public class UniAwaiterImpl implements UniAwaiter {
	@Override
	public <T> T await(Supplier<Uni<T>> call) {
		try {
			return VertxContextSupport.subscribeAndAwait(call);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalStateException("A reactive call failed", e);
		}
	}
}
