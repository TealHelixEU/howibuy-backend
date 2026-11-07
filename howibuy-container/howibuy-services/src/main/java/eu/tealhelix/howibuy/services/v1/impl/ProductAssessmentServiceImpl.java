package eu.tealhelix.howibuy.services.v1.impl;

import static java.util.stream.Collectors.toSet;

import static eu.tealhelix.common.utils.UniComprehensions.forc;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_OTHER;

import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.howibuy.services.v1.ProductAssessmentService;
import eu.tealhelix.howibuy.v1.model.AlternativeForProduct;
import eu.tealhelix.howibuy.v1.model.ImmutableAlternativeForProduct;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ListedProduct;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
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
	private final CatalogueAssortmentService catalogueAssortmentService;

	@Inject
	public ProductAssessmentServiceImpl(
			TealHelixAuthorization authorization,
			ReferenceProductIdentifier referenceProductIdentifier,
			SingleProductAssessor singleProductAssessor,
			PersonalWeightsProvider personalWeightsProvider,
			CatalogueAssortmentService catalogueAssortmentService
	) {
		this.authorization = authorization;
		this.referenceProductIdentifier = referenceProductIdentifier;
		this.singleProductAssessor = singleProductAssessor;
		this.personalWeightsProvider = personalWeightsProvider;
		this.catalogueAssortmentService = catalogueAssortmentService;
	}

	@Override
	public Uni<ProductAssessmentOutcome> assessSingleProduct(User user, CatalogueId catalogueId, ProductKey productKey, String language) {
		authorization.requirePerson(user);
		RequiredInputMissingException.throwIfRequiredInputMissing("language", language);
		RequiredInputMissingException.throwIfRequiredInputMissing("productKey", productKey);
		LOG.info("Assess single product, user: {}, catalogue: {}, language: {}, key: {}", user.getId().asString(), catalogueId.asString(), language, productKey.asString());
		return forc(
				personalWeightsProvider.forUser(user).map(WeightProfileAndAttemptId::profile),
				_ -> referenceProductIdentifier.identify(catalogueId, Set.of(productKey)),
				(_, _) -> catalogueAssortmentService.stockedArchetypes(catalogueId),
				(personalProfile, identifications, stocked) -> singleProductAssessor.assessOne(user.getId(), productKey, identifications.get(productKey), personalProfile, stocked),
				(_, _, _, outcome) -> withListedProducts(catalogueId, language, List.of(outcome)))
				.map(List::getFirst);
	}

	@Override
	public Uni<List<ProductAssessmentOutcome>> assessMultipleProductsSync(User user, CatalogueId catalogueId, List<ProductKey> productKeys, String language) {
		authorization.requirePerson(user);
		AssessmentInputs.requireLanguageAndProductKeys(language, productKeys);
		return assessBatch(user, catalogueId, productKeys, language);
	}

	/**
	 * Assesses each product in order, one outcome per key sent. Every per-product failure is isolated into that
	 * product's outcome by {@link #assessOneIsolated}, so a failure in one product never terminates the batch.
	 * <p>
	 * The user's weighting profile, the identifications of the products, the archetypes the Catalogue stocks and the
	 * products that stand for the Alternatives are each read once for the whole batch, so the batch issues the same
	 * queries whatever its size. The products are read after every product is assessed, so the outcomes are complete
	 * only once they all are.
	 */
	private Uni<List<ProductAssessmentOutcome>> assessBatch(User user, CatalogueId catalogueId, List<ProductKey> productKeys, String language) {
		LOG.info("Assess (batch) products, user: {}, catalogue: {}, language: {}, keys: {}", user.getId().asString(), catalogueId.asString(), language,
				productKeys.stream().map(ProductKey::asString).toList());
		return forc(
				personalWeightsProvider.forUser(user).map(WeightProfileAndAttemptId::profile),
				_ -> referenceProductIdentifier.identify(catalogueId, Set.copyOf(productKeys)),
				(_, _) -> catalogueAssortmentService.stockedArchetypes(catalogueId),
				(personalProfile, identifications, stocked) -> assessProducts(user.getId(), productKeys, identifications, personalProfile, stocked),
				(_, _, _, outcomes) -> withListedProducts(catalogueId, language, outcomes));
	}

	private Uni<List<ProductAssessmentOutcome>> assessProducts(
			UserId userId,
			List<ProductKey> productKeys,
			Map<ProductKey, ProductIdentification> identifications,
			WeightProfile personalProfile,
			Set<ArchetypeProductId> stocked
	) {
		return Multi.createFrom().iterable(productKeys)
				.onItem().transformToUniAndConcatenate(productKey -> assessOneIsolated(userId, productKey, identifications.get(productKey), personalProfile, stocked))
				.collect().asList();
	}

	private Uni<ProductAssessmentOutcome> assessOneIsolated(
			UserId userId, ProductKey productKey, ProductIdentification identification, WeightProfile personalProfile, Set<ArchetypeProductId> stocked) {
		return singleProductAssessor.assessOne(userId, productKey, identification, personalProfile, stocked)
				.onFailure().recoverWithItem(failure -> {
					LOG.error("Unexpected failure assessing product in batch, key: {}", productKey.asString(), failure);
					return SingleProductAssessor.outcome(productKey, FAILURE_OTHER);
				});
	}

	/**
	 * Names the product of the Catalogue that stands for the archetype of each Alternative, reading those of all the
	 * outcomes at once.
	 */
	private Uni<List<ProductAssessmentOutcome>> withListedProducts(CatalogueId catalogueId, String language, List<ProductAssessmentOutcome> outcomes) {
		Set<ArchetypeProductId> alternativeArchetypeIds = outcomes.stream()
				.flatMap(outcome -> outcome.getAlternatives().stream())
				.map(alternative -> alternative.getArchetype().getId())
				.collect(toSet());
		return catalogueAssortmentService.listedProducts(catalogueId, alternativeArchetypeIds, language)
				.map(products -> outcomes.stream()
						.<ProductAssessmentOutcome>map(outcome -> ImmutableProductAssessmentOutcome.copyOf(outcome)
								.withAlternatives(outcome.getAlternatives().stream()
										.map(alternative -> withListedProduct(alternative, products))
										.toList()))
						.toList());
	}

	private static AlternativeForProduct withListedProduct(AlternativeForProduct alternative, Map<ArchetypeProductId, ListedProduct> products) {
		return ImmutableAlternativeForProduct.copyOf(alternative).withProduct(products.get(alternative.getArchetype().getId()));
	}
}
