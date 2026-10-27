package eu.tealhelix.howibuy.services.v1.impl;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.howibuy.dao.ClassificationDao;
import eu.tealhelix.howibuy.services.model.ArchetypeListing;
import eu.tealhelix.howibuy.services.v1.alternatives.AlternativeProductSelector;
import eu.tealhelix.howibuy.v1.model.ImmutableListedProduct;
import eu.tealhelix.howibuy.v1.model.ListedProduct;
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
	private final AlternativeProductSelector alternativeProductSelector;

	@Inject
	public CatalogueAssortmentService(
			ReactivePersistenceContextFactory persistenceContextFactory,
			ClassificationDao classificationDao,
			AlternativeProductSelector alternativeProductSelector
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.classificationDao = classificationDao;
		this.alternativeProductSelector = alternativeProductSelector;
	}

	/**
	 * @param catalogueId The Catalogue
	 * @return The archetypes that a product with an active Listing in the Catalogue is classified to
	 */
	public Uni<Set<ArchetypeProductId>> stockedArchetypes(CatalogueId catalogueId) {
		return persistenceContextFactory.withoutTransaction(em -> classificationDao.findStockedArchetypes(em, catalogueId));
	}

	/**
	 * @param catalogueId  The Catalogue
	 * @param archetypeIds The recommended archetypes
	 * @param language     The language the user asked to be answered in
	 * @return The product of the Catalogue that {@link AlternativeProductSelector} picks to stand for each archetype,
	 *         among those with an active Listing; an archetype with none has no product
	 */
	public Uni<Map<ArchetypeProductId, ListedProduct>> listedProducts(CatalogueId catalogueId, Set<ArchetypeProductId> archetypeIds, String language) {
		return persistenceContextFactory.withoutTransaction(em -> classificationDao.findListingsOfArchetypes(em, catalogueId, archetypeIds, language))
				.map(listings -> listings.stream().collect(groupingBy(
						ArchetypeListing::getArchetypeProductId,
						collectingAndThen(toList(), listingsOfArchetype -> toListedProduct(alternativeProductSelector.select(listingsOfArchetype), language)))));
	}

	private static ListedProduct toListedProduct(ArchetypeListing listing, String language) {
		var nameLanguage = listing.getNames().containsKey(language) ? language : listing.getPrimaryLanguage();
		return ImmutableListedProduct.builder()
				.productKey(listing.getProductKey())
				.name(listing.getNames().get(nameLanguage))
				.language(nameLanguage)
				.price(withoutTrailingZeros(listing.getPrice()))
				.currency(listing.getCurrency())
				.build();
	}

	/**
	 * Stripping the trailing zeros of a whole number leaves a negative scale (10 becomes 1E+1), which is brought back to
	 * zero so that the price prints as 10.
	 */
	private static BigDecimal withoutTrailingZeros(BigDecimal price) {
		var stripped = price.stripTrailingZeros();
		return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
	}
}
