package eu.tealhelix.howibuy.services.v1.impl;

import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.howibuy.dao.ClassificationDao;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import io.smallrye.mutiny.Uni;

/**
 * What a Catalogue stocks, as the recommendation sees it: an Alternative is only ever something the user can buy there.
 * <p>
 * Read from the database on every request. It changes with every Product Change and Classification, and nothing has
 * measured it slow enough to be worth keeping per Catalogue.
 */
@ApplicationScoped
public class CatalogueAssortmentService {
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final ClassificationDao classificationDao;

	@Inject
	public CatalogueAssortmentService(ReactivePersistenceContextFactory persistenceContextFactory, ClassificationDao classificationDao) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.classificationDao = classificationDao;
	}

	/**
	 * @param catalogueId The Catalogue
	 * @return The archetypes that a product with an active Listing in the Catalogue is classified to
	 */
	public Uni<Set<ArchetypeProductId>> stockedArchetypes(CatalogueId catalogueId) {
		return persistenceContextFactory.withoutTransaction(em -> classificationDao.findStockedArchetypes(em, catalogueId));
	}
}
