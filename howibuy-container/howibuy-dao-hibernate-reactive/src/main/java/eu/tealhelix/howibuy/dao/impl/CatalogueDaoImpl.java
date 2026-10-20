package eu.tealhelix.howibuy.dao.impl;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.types.RepresentableAsString;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import eu.tealhelix.howibuy.dao.CatalogueDao;
import eu.tealhelix.howibuy.dao.jpa.CatalogueEntity;
import eu.tealhelix.howibuy.dao.jpa.CatalogueEntity_;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class CatalogueDaoImpl implements CatalogueDao {
	@Override
	public Uni<Map<CatalogueId, RetailerId>> findRetailers(ReactivePersistenceContext em, Set<CatalogueId> catalogueIds) {
		if (catalogueIds.isEmpty()) return Uni.createFrom().item(Map.of());
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(CatalogueEntity.class);
		var catalogue = q.from(CatalogueEntity.class);
		q.where(catalogue.get(CatalogueEntity_.id).in(catalogueIds.stream().map(RepresentableAsString::asString).toList()));
		return em.createQuery(q).getResultList().map(catalogues -> catalogues.stream().collect(Collectors.toMap(
				c -> new CatalogueIdImpl(c.getId()),
				c -> new GenericRetailerId(c.getRetailerId().toString())
		)));
	}
}
