package eu.tealhelix.common.dao.reactive.hibernate;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactiveQuery;
import eu.tealhelix.common.dao.reactive.ReactiveUpdate;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny.Session;

class ReactivePersistenceContextImpl implements ReactivePersistenceContext {
	protected final Session session;

	public ReactivePersistenceContextImpl(Session session) {
		this.session = session;
	}

	@Override
	public Uni<Void> flush() {
		return session.flush();
	}

	@Override
	public <T> Uni<T> flush(T t) {
		return session.flush().replaceWith(t);
	}

	@Override
	public <T> Uni<T> find(Class<T> entityClass, Object id) {
		return session.find(entityClass, id);
	}

	@Override
	public <T> T getReference(Class<T> entityClass, Object id) {
		return session.getReference(entityClass, id);
	}

	@Override
	public CriteriaBuilder getCriteriaBuilder() {
		// TODO Double check this
		return session.getFactory().getCriteriaBuilder();
	}

	@Override
	public <R> ReactiveQuery<R> createQuery(CriteriaQuery<R> criteriaQuery) {
		return new ReactiveQueryImpl<>(session.createQuery(criteriaQuery));
	}

	@Override
	public <R> ReactiveUpdate createUpdate(CriteriaUpdate<R> criteriaUpdate) {
		return new ReactiveUpdateImpl(session.createQuery(criteriaUpdate));
	}
}
