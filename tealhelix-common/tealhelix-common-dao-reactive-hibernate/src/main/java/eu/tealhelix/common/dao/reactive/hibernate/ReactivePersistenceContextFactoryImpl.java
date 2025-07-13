package eu.tealhelix.common.dao.reactive.hibernate;

import java.util.function.Function;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny.SessionFactory;

@ApplicationScoped
public class ReactivePersistenceContextFactoryImpl implements ReactivePersistenceContextFactory {

	private SessionFactory sessionFactory;

	@SuppressWarnings("unused")
	ReactivePersistenceContextFactoryImpl() {
		// INTENTIONALLY BLANK
	}

	@Inject
	public ReactivePersistenceContextFactoryImpl(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	@Override
	public <T> Uni<T> withTransaction(Function<ReactivePersistenceTxContext, Uni<T>> work) {
		return sessionFactory.withTransaction((s, tx) -> {
			var persistenceContext = new ReactivePersistenceTxContextImpl(sessionFactory, s, tx);
			return work.apply(persistenceContext);
		});
	}

	@Override
	public void close() {
		if (sessionFactory != null) {
			sessionFactory.close();
		}
	}
}
