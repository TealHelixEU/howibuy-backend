package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;
import static eu.tealhelix.common.utils.UniComprehensions.forcm;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_OTHER;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.services.v1.ProductAssessmentService;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ProductAssessmentServiceImpl implements ProductAssessmentService {
	private static final Logger LOG = LoggerFactory.getLogger(ProductAssessmentServiceImpl.class);

	private final TealHelixAuthorization authorization;
	private final ReferenceProductIdentifier referenceProductIdentifier;
	private final SingleProductAssessor singleProductAssessor;
	private final PersonalWeightsProvider personalWeightsProvider;

	@Inject
	public ProductAssessmentServiceImpl(
			TealHelixAuthorization authorization,
			ReferenceProductIdentifier referenceProductIdentifier,
			SingleProductAssessor singleProductAssessor,
			PersonalWeightsProvider personalWeightsProvider
	) {
		this.authorization = authorization;
		this.referenceProductIdentifier = referenceProductIdentifier;
		this.singleProductAssessor = singleProductAssessor;
		this.personalWeightsProvider = personalWeightsProvider;
	}

	@Override
	public Uni<ProductAssessmentOutcome> assessSingleProduct(User user, CatalogueId catalogueId, ProductKey productKey) {
		authorization.requirePerson(user);
		LOG.info("Assess single product, user: {}, catalogue: {}, key: {}", user.getId().asString(), catalogueId.asString(), productKey.asString());
		return forc(
				personalWeightsProvider.forUser(user),
				_ -> referenceProductIdentifier.identify(catalogueId, Set.of(productKey)),
				(personalProfile, identifications) -> singleProductAssessor.assessOne(productKey, identifications.get(productKey), personalProfile));
	}

	@Override
	public Uni<List<ProductAssessmentOutcome>> assessMultipleProductsSync(User user, CatalogueId catalogueId, List<ProductKey> productKeys) {
		authorization.requirePerson(user);
		return assessBatch(user, catalogueId, productKeys).collect().asList();
	}

	@Override
	public Multi<ProductAssessmentOutcome> assessMultipleProductsAsync(User user, CatalogueId catalogueId, List<ProductKey> productKeys) {
		authorization.requirePerson(user);
		return assessBatch(user, catalogueId, productKeys);
	}

	/**
	 * Assesses each product in order, emitting one outcome per key sent. Every per-product failure is isolated into that
	 * product's outcome by {@link #assessOneIsolated}, so a failure in one product never terminates the batch.
	 * <p>
	 * The user's weighting profile and the identifications of the products are each read once for the whole batch, so
	 * the batch issues the same queries whatever its size.
	 */
	private Multi<ProductAssessmentOutcome> assessBatch(User user, CatalogueId catalogueId, List<ProductKey> productKeys) {
		LOG.info("Assess (batch) products, user: {}, catalogue: {}, keys: {}", user.getId().asString(), catalogueId.asString(),
				productKeys.stream().map(ProductKey::asString).toList());
		return forcm(
				personalWeightsProvider.forUser(user),
				_ -> referenceProductIdentifier.identify(catalogueId, Set.copyOf(productKeys)),
				(personalProfile, identifications) -> assessProducts(productKeys, identifications, personalProfile))
				.onItem().transformToMulti(Function.identity());
	}

	private Multi<ProductAssessmentOutcome> assessProducts(
			List<ProductKey> productKeys, Map<ProductKey, ProductIdentification> identifications, WeightProfile personalProfile) {
		return Multi.createFrom().iterable(productKeys)
				.onItem().transformToUniAndConcatenate(productKey -> assessOneIsolated(productKey, identifications.get(productKey), personalProfile));
	}

	private Uni<ProductAssessmentOutcome> assessOneIsolated(ProductKey productKey, ProductIdentification identification, WeightProfile personalProfile) {
		return singleProductAssessor.assessOne(productKey, identification, personalProfile)
				.onFailure().recoverWithItem(failure -> {
					LOG.error("Unexpected failure assessing product in batch, key: {}", productKey.asString(), failure);
					return SingleProductAssessor.outcome(productKey, FAILURE_OTHER);
				});
	}
}
