package eu.tealhelix.common.test.jpa;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.dao.reactive.ReactiveQuery;
import eu.tealhelix.common.dao.reactive.ReactiveUpdate;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * An implementation of {@link ReactivePersistenceContextFactory} for using in JUnit 5 tests.
 * It remembers all opened transactions and sessions so that you can run assertions on them.
 * It implements JUnit 5's extension API {@code AfterEachCallback} to clear the list of opened
 * transactions and sessions.
 * <p>
 * Example usage:
 * <code><pre>
 *     &#064;RegisterExtension
 *     &#064;Produces
 *     private MockReactivePersistenceContextFactory mockReactivePersistenceContextFactory =
 *         new MockReactivePersistenceContextFactory();
 * </pre></code>
 */
public class MockReactivePersistenceContextFactory implements ReactivePersistenceContextFactory, AfterEachCallback {
	public sealed interface PersistenceAction {
	}

	public record PersistAction(Object persistedObject) implements PersistenceAction {
	}

	public record MergeAction(Object mergedObject) implements PersistenceAction {
	}

	public record RemoveAction(Object persistedObject) implements PersistenceAction {
	}

	public record RollbackAction() implements PersistenceAction {
	}

	public record FindAction(Class<?> entityClass, Object id) implements PersistenceAction {
	}

	public record FlushAction() implements PersistenceAction {
	}

	public static class MockReactivePersistenceTxContext implements ReactivePersistenceTxContext {
		private boolean markedForRollback;
		private final List<PersistenceAction> actions = new ArrayList<>();

		@Override
		public boolean isMarkedForRollback() {
			return markedForRollback;
		}

		@Override
		public void markForRollback() {
			if (!markedForRollback) {
				actions.add(new RollbackAction());
			}
			markedForRollback = true;
		}

		@Override
		public <T> Uni<T> persist(T entity) {
			actions.add(new PersistAction(entity));
			return Uni.createFrom().item(entity);
		}

		@Override
		public Uni<Void> persistAll(Object... entities) {
			Stream.of(entities).map(PersistAction::new).forEach(actions::add);
			return Uni.createFrom().nullItem();
		}

		@Override
		public <T> Uni<T> merge(T entity) {
			actions.add(new MergeAction(entity));
			// TODO Implement the merger, a mock Function<X, X> that will be able to return the output entity
			return Uni.createFrom().item(entity);
		}

		@Override
		public Uni<Void> remove(Object entity) {
			actions.add(new RemoveAction(entity));
			return Uni.createFrom().nullItem();
		}

		@Override
		public Uni<Void> removeAll(Object... entities) {
			Stream.of(entities).map(RemoveAction::new).forEach(actions::add);
			return Uni.createFrom().nullItem();
		}

		@Override
		public Uni<Void> flush() {
			actions.add(new FlushAction());
			return Uni.createFrom().nullItem();
		}

		@Override
		public <T> Uni<T> flush(T t) {
			return flush().replaceWith(t);
		}

		@Override
		public <T> Uni<T> find(Class<T> entityClass, Object id) {
			actions.add(new FindAction(entityClass, id));
			return Uni.createFrom().nullItem(); // TODO Make it return something useful, if configured so
		}

		@Override
		public <T> T getReference(Class<T> entityClass, Object id) {
			return null; // TODO Make it return something useful, if configured so
		}

		@Override
		public CriteriaBuilder getCriteriaBuilder() {
			return mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
		}

		@Override
		public <R> ReactiveQuery<R> createQuery(CriteriaQuery<R> criteriaQuery) {
			@SuppressWarnings("unchecked")
			ReactiveQuery<R> m = mock(ReactiveQuery.class, RETURNS_DEEP_STUBS);
			return m;
		}

		@Override
		public <R> ReactiveUpdate createUpdate(CriteriaUpdate<R> criteriaUpdate) {
			ReactiveUpdate m = mock(ReactiveUpdate.class, RETURNS_DEEP_STUBS);
			return m;
		}

		public List<PersistenceAction> getActions() {
			return actions;
		}
	}

	private final List<MockReactivePersistenceTxContext> openedTransactions = new ArrayList<>();

	@Override
	public <T> Uni<T> withTransaction(Function<ReactivePersistenceTxContext, Uni<T>> work) {
		var tx = new MockReactivePersistenceTxContext();
		openedTransactions.add(tx);
		return work.apply(tx);
	}

	@Override
	public void close() {
		// NOOP
	}

	public List<MockReactivePersistenceTxContext> getOpenedTransactions() {
		return openedTransactions;
	}

	@Override
	public void afterEach(ExtensionContext extensionContext) {
		openedTransactions.clear();
	}
}
