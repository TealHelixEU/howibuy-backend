package eu.tealhelix.common.dao.reactive.hibernate;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.NoResultException;

import eu.tealhelix.common.dao.reactive.ReactiveQuery;
import eu.tealhelix.common.types.entity.NotFoundException;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny.SelectionQuery;

class ReactiveQueryImpl<R> implements ReactiveQuery<R> {
	private final SelectionQuery<R> query;

	public ReactiveQueryImpl(SelectionQuery<R> query) {
		this.query = query;
	}

	@Override
	public Uni<List<R>> getResultList() {
		return query.getResultList();
	}

	@Override
	public Uni<R> getSingleResult() {
		return query.getSingleResult()
				.onFailure(NoResultException.class)
				.transform(nre -> new NotFoundException("No entity matches the criteria", nre)); // XXX use DAO exception here, translate in the service layer!
	}

	@Override
	public Uni<Optional<R>> getSingleOptionalResult() {
		return query.getSingleResultOrNull().map(Optional::ofNullable);
	}

	@Override
	public ReactiveQuery<R> setFirstResult(int firstResult) {
		query.setFirstResult(firstResult);
		return this;
	}

	@Override
	public ReactiveQuery<R> setMaxResults(int maxResults) {
		query.setMaxResults(maxResults);
		return this;
	}

	@Override
	public ReactiveQuery<R> setParameter(String parameter, Object argument) {
		query.setParameter(parameter, argument);
		return this;
	}
}
