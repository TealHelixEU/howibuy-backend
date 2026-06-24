package eu.tealhelix.howibuy.dao;

import eu.tealhelix.howibuy.v1.types.RetailerId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import io.smallrye.mutiny.Uni;

public interface RetailerDao {
	Uni<Boolean> exists(ReactivePersistenceContext em, RetailerId retailerId);
}
