package eu.tealhelix.catalogue.dao.impl;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.catalogue.dao.CatalogueDao;
import eu.tealhelix.catalogue.dao.jpa.CatalogueEntity;
import eu.tealhelix.catalogue.dao.jpa.CatalogueEntity_;
import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableCatalogue;
import eu.tealhelix.catalogue.v1.types.HasCatalogueId;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.v1.types.HasRetailerId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class CatalogueDaoImpl implements CatalogueDao {
	@Override
	public Uni<Catalogue> find(ReactivePersistenceContext em, HasCatalogueId catalogueId) {
		return em.find(CatalogueEntity.class, catalogueId.getId().asString()).map(entity -> entity != null ? toCatalogue(entity) : null);
	}

	@Override
	public Uni<List<Catalogue>> findActive(ReactivePersistenceContext em, HasRetailerId retailerId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(CatalogueEntity.class);
		var catalogue = q.from(CatalogueEntity.class);
		q.where(
				cb.equal(catalogue.get(CatalogueEntity_.retailerId), retailerId.getId().asUuid()),
				cb.isTrue(catalogue.get(CatalogueEntity_.active))
		);
		q.orderBy(cb.asc(catalogue.get(CatalogueEntity_.id)));
		return em.createQuery(q).getResultList().map(catalogues -> catalogues.stream().map(CatalogueDaoImpl::toCatalogue).toList());
	}

	private static Catalogue toCatalogue(CatalogueEntity entity) {
		return ImmutableCatalogue.builder()
				.id(new CatalogueIdImpl(entity.getId()))
				.retailerId(new GenericRetailerId(entity.getRetailerId().toString()))
				.country(entity.getCountry())
				.primaryLanguage(entity.getPrimaryLanguage())
				.acceptedLanguages(entity.getAcceptedLanguages())
				.adapter(entity.getAdapter())
				.isActive(entity.isActive())
				.build();
	}
}
