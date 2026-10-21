package eu.tealhelix.catalogue.dao.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.catalogue.dao.CatalogueDao;
import eu.tealhelix.catalogue.dao.jpa.CatalogueEntity;
import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableCatalogue;
import eu.tealhelix.catalogue.v1.types.HasCatalogueId;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class CatalogueDaoImpl implements CatalogueDao {
	@Override
	public Uni<Catalogue> find(ReactivePersistenceContext em, HasCatalogueId catalogueId) {
		return em.find(CatalogueEntity.class, catalogueId.getId().asString()).map(entity -> entity != null ? toCatalogue(entity) : null);
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
