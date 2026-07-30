package eu.tealhelix.howibuy.dao.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.howibuy.dao.RetailerDao;
import eu.tealhelix.howibuy.dao.jpa.RetailerEntity;
import eu.tealhelix.howibuy.v1.types.RetailerId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RetailerDaoImpl implements RetailerDao {
	@Override
	public Uni<Boolean> findActiveFlag(ReactivePersistenceContext em, RetailerId retailerId) {
		return em.find(RetailerEntity.class, retailerId.asUuid())
				.map(retailer -> retailer == null ? null : retailer.isActive());
	}
}
