package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forcm;
import static eu.tealhelix.howibuy.v1.types.IdentificationFailureReason.NOT_CLASSIFIED;
import static eu.tealhelix.howibuy.v1.types.IdentificationFailureReason.NO_MATCHING_ARCHETYPE;
import static eu.tealhelix.howibuy.v1.types.IdentificationFailureReason.UNKNOWN_PRODUCT;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.howibuy.dao.ClassificationDao;
import eu.tealhelix.howibuy.services.model.ProductToAssess;
import eu.tealhelix.howibuy.v1.model.ImmutableIdentificationDiagnostics;
import io.smallrye.mutiny.Uni;

/**
 * Identifies the Archetype Product of scanned products by looking up their Classifications: the key of a product names
 * a Retailer Product of the Retailer of the user's Catalogue, and its Classification names the archetype.
 */
@ApplicationScoped
public class ReferenceProductIdentifier {
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final ClassificationDao classificationDao;
	private final ArchetypeCorpus archetypeCorpus;

	@Inject
	public ReferenceProductIdentifier(
			ReactivePersistenceContextFactory persistenceContextFactory,
			ClassificationDao classificationDao,
			ArchetypeCorpus archetypeCorpus
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.classificationDao = classificationDao;
		this.archetypeCorpus = archetypeCorpus;
	}

	/**
	 * Identify the products all at once, with one query whatever their number. A product is identified by its
	 * Classification as it stands, also when the product changed since it was classified, or the Classification was
	 * reopened or needs review; the diagnostics tell.
	 *
	 * @param catalogueId The Catalogue the user scans the products in
	 * @param productKeys The keys of the products
	 * @return The identification of each product, by its key
	 */
	public Uni<Map<ProductKey, ProductIdentification>> identify(CatalogueId catalogueId, Set<ProductKey> productKeys) {
		return forcm(
				archetypeCorpus.scoredArchetypes(),
				_ -> persistenceContextFactory.withoutTransaction(em -> classificationDao.findProductsToAssess(em, catalogueId, productKeys)),
				(archetypes, products) -> identifications(productKeys, products, archetypes));
	}

	private static Map<ProductKey, ProductIdentification> identifications(
			Set<ProductKey> productKeys, List<ProductToAssess> products, ScoredArchetypes archetypes) {
		var identifications = new HashMap<ProductKey, ProductIdentification>(productKeys.size());
		productKeys.forEach(productKey -> identifications.put(productKey, new ProductIdentification.NotIdentified(UNKNOWN_PRODUCT, null)));
		products.forEach(product -> identifications.put(product.getProductKey(), identification(product, archetypes)));
		return identifications;
	}

	private static ProductIdentification identification(ProductToAssess product, ScoredArchetypes archetypes) {
		var diagnostics = ImmutableIdentificationDiagnostics.builder().retailerProductId(product.getId());
		var classification = product.getClassification();
		if (classification == null) return new ProductIdentification.NotIdentified(NOT_CLASSIFIED, diagnostics.build());

		diagnostics.classifier(classification.getClassifier())
				.needsReview(classification.isNeedsReview())
				.pending(product.isPending());
		var archetypeProductId = classification.getArchetypeProductId();
		if (archetypeProductId == null) return new ProductIdentification.NotIdentified(NO_MATCHING_ARCHETYPE, diagnostics.build());

		return new ProductIdentification.Identified(archetypeProductId, diagnostics
				.archetypeProductId(archetypeProductId)
				.archetypeProductName(archetypes.nameOf(archetypeProductId).orElse(null))
				.build());
	}
}
