package eu.tealhelix.betterme.dao;

import eu.tealhelix.betterme.v1.types.RetailerId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.HasUserId;
import io.smallrye.mutiny.Uni;

public interface CorrelationIdDao {
	Uni<Void> createCorrelation(ReactivePersistenceTxContext tx, RetailerId retailerId, String correlationId, HasUserId userId);

	Uni<User> requireByRetailerAndCorrelationId(ReactivePersistenceContext em, RetailerId retailerId, String correlationId);
}
