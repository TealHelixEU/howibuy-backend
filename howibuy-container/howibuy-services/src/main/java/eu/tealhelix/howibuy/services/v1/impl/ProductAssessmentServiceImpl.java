package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_OTHER;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.services.v1.ProductAssessmentService;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductData;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ProductAssessmentServiceImpl implements ProductAssessmentService {
	private static final Logger LOG = LoggerFactory.getLogger(ProductAssessmentServiceImpl.class);

	private final TealHelixAuthorization authorization;
	private final SingleProductAssessor singleProductAssessor;
	private final PersonalWeightsProvider personalWeightsProvider;

	@Inject
	public ProductAssessmentServiceImpl(
			TealHelixAuthorization authorization,
			SingleProductAssessor singleProductAssessor,
			PersonalWeightsProvider personalWeightsProvider
	) {
		this.authorization = authorization;
		this.singleProductAssessor = singleProductAssessor;
		this.personalWeightsProvider = personalWeightsProvider;
	}

	@Override
	public Uni<ProductAssessmentOutcome> assessSingleProduct(User user, ProductData productData) {
		authorization.requireUserNotService(user);
		LOG.info("Assess single product as user {}: {}", user.getId().asString(), ProductData.toLogString(productData));
		return personalWeightsProvider.forUser(user)
				.flatMap(personalProfile -> singleProductAssessor.assessOne(productData, personalProfile));
	}

	@Override
	public Uni<List<ProductAssessmentOutcome>> assessMultipleProductsSync(User user, List<ProductData> productsData) {
		authorization.requireUserNotService(user);
		return assessBatch(user, productsData).collect().asList();
	}

	@Override
	public Multi<ProductAssessmentOutcome> assessMultipleProductsAsync(User user, List<ProductData> productsData) {
		authorization.requireUserNotService(user);
		return assessBatch(user, productsData);
	}

	/**
	 * Assesses each product in order, emitting one outcome per product. Every per-product failure — an AI signal or an
	 * unexpected error — is isolated into that product's outcome by {@link #assessOneIsolated}, so a failure in one
	 * product never terminates the batch.
	 * <p>
	 * The user's weighting profile is resolved once for the whole batch: it is a property of the user, not of the
	 * product being assessed, so reading it per product would only repeat the same three queries.
	 */
	private Multi<ProductAssessmentOutcome> assessBatch(User user, List<ProductData> productsData) {
		return personalWeightsProvider.forUser(user)
				.onItem().transformToMulti(personalProfile -> assessProducts(user, personalProfile, productsData));
	}

	private Multi<ProductAssessmentOutcome> assessProducts(User user, WeightProfile personalProfile, List<ProductData> productsData) {
		return Multi.createFrom().iterable(productsData)
				.onItem().transformToUniAndConcatenate(productData -> {
					LOG.info("Assess (batch) product as user {}: {}", user.getId().asString(), ProductData.toLogString(productData));
					return assessOneIsolated(productData, personalProfile);
				});
	}

	private Uni<ProductAssessmentOutcome> assessOneIsolated(ProductData productData, WeightProfile personalProfile) {
		return singleProductAssessor.assessOne(productData, personalProfile)
				.onFailure().recoverWithItem(failure -> {
					LOG.error("Unexpected failure assessing product in batch, key: {}",
							productData.getProductKey().asString(), failure);
					return SingleProductAssessor.outcome(productData, FAILURE_OTHER, null);
				});
	}
}
