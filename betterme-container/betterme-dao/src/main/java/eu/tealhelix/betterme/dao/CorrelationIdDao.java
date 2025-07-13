package eu.tealhelix.betterme.dao;

import eu.tealhelix.betterme.v1.types.RetailerId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.v1.model.User;
import io.smallrye.mutiny.Uni;

public interface CorrelationIdDao {
	Uni<User> requireByRetailerAndCorrelationId(ReactivePersistenceContext em, RetailerId retailerId, String correlationId);
}
