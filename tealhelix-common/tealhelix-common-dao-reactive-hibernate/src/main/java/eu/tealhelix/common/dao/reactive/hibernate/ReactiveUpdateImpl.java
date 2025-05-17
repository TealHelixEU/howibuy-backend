package eu.tealhelix.common.dao.reactive.hibernate;

import eu.tealhelix.common.dao.reactive.ReactiveUpdate;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny.MutationQuery;

class ReactiveUpdateImpl implements ReactiveUpdate {
	private final MutationQuery query;

	public ReactiveUpdateImpl(MutationQuery query) {
		this.query = query;
	}

	@Override
	public Uni<Integer> execute() {
		return query.executeUpdate();
	}

	@Override
	public ReactiveUpdate setParameter(String parameter, Object argument) {
		query.setParameter(parameter, argument);
		return this;
	}
}
