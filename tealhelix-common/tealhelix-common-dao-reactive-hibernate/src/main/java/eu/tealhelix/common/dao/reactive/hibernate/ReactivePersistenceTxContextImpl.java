package eu.tealhelix.common.dao.reactive.hibernate;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny.Session;
import org.hibernate.reactive.mutiny.Mutiny.Transaction;

class ReactivePersistenceTxContextImpl extends ReactivePersistenceContextImpl implements ReactivePersistenceTxContext {
	private final Transaction transaction;

	public ReactivePersistenceTxContextImpl(Session session, Transaction transaction) {
		super(session);
		this.transaction = transaction;
	}

	@Override
	public <T> Uni<T> persist(T entity) {
		return session.persist(entity).replaceWith(entity);
	}

	@Override
	public Uni<Void> persistAll(Object... entities) {
		return session.persistAll(entities);
	}

	@Override
	public <T> Uni<T> merge(T entity) {
		return session.merge(entity);
	}

	@Override
	public Uni<Void> remove(Object entity) {
		return session.remove(entity);
	}

	@Override
	public Uni<Void> removeAll(Object... entities) {
		return session.removeAll(entities);
	}

	@Override
	public boolean isMarkedForRollback() {
		return transaction.isMarkedForRollback();
	}

	@Override
	public void markForRollback() {
		transaction.markForRollback();
	}
}
