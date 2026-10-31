package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forcm;
import static eu.tealhelix.howibuy.v1.types.BasketAssessmentOutcomeType.ASSESSED;
import static eu.tealhelix.howibuy.v1.types.BasketAssessmentOutcomeType.NOTHING_TO_ASSESS;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.dao.UserProfileDao;
import eu.tealhelix.howibuy.scoring.v1.ScoredProduct;
import eu.tealhelix.howibuy.services.v1.BasketAssessmentService;
import eu.tealhelix.howibuy.services.v1.basket.BasketGradeLabels;
import eu.tealhelix.howibuy.services.v1.basket.BasketRules;
import eu.tealhelix.howibuy.services.v1.basket.BasketRules.BasketVerdict;
import eu.tealhelix.howibuy.services.v1.basket.BasketRules.ScoredBasketProduct;
import eu.tealhelix.howibuy.v1.model.BasketAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ImmutableBasketAssessmentOutcome;
import eu.tealhelix.howibuy.v1.types.WeightProfile;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class BasketAssessmentServiceImpl implements BasketAssessmentService {
	private static final Logger LOG = LoggerFactory.getLogger(BasketAssessmentServiceImpl.class);

	private final TealHelixAuthorization authorization;
	private final ReferenceProductIdentifier referenceProductIdentifier;
	private final ArchetypeCorpus archetypeCorpus;
	private final PersonalWeightsProvider personalWeightsProvider;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final UserProfileDao userProfileDao;
	private final BasketRules basketRules = new BasketRules();
	private final BasketGradeLabels basketGradeLabels = new BasketGradeLabels();

	/**
	 * A product of the basket that has an Overall Score under the Weighting Profile in force.
	 *
	 * @param productKey    the retailer's key of the product
	 * @param scoredProduct the archetype the product was identified as, with its normalised scores
	 * @param overallScore  the Overall Score of the archetype under the Weighting Profile in force
	 */
	private record ScoredBasketLine(ProductKey productKey, ScoredProduct scoredProduct, double overallScore) {
		ScoredBasketProduct toBasketProduct() {
			return new ScoredBasketProduct(productKey, overallScore);
		}
	}

	@Inject
	public BasketAssessmentServiceImpl(
			TealHelixAuthorization authorization,
			ReferenceProductIdentifier referenceProductIdentifier,
			ArchetypeCorpus archetypeCorpus,
			PersonalWeightsProvider personalWeightsProvider,
			ReactivePersistenceContextFactory persistenceContextFactory,
			UserProfileDao userProfileDao
	) {
		this.authorization = authorization;
		this.referenceProductIdentifier = referenceProductIdentifier;
		this.archetypeCorpus = archetypeCorpus;
		this.personalWeightsProvider = personalWeightsProvider;
		this.persistenceContextFactory = persistenceContextFactory;
		this.userProfileDao = userProfileDao;
	}

	@Override
	public Uni<BasketAssessmentOutcome> assessBasketPrePurchase(User user, CatalogueId catalogueId, List<ProductKey> productKeys, String language) {
		authorization.requirePerson(user);
		LOG.info("Assess basket before purchase, user: {}, catalogue: {}, language: {}, keys: {}", user.getId().asString(), catalogueId.asString(), language,
				productKeys.stream().map(ProductKey::asString).toList());
		return forcm(
				scoreBasket(user, catalogueId, productKeys),
				_ -> persistenceContextFactory.withoutTransaction(em -> userProfileDao.requireXpTotal(em, user.getId())),
				(lines, totalXp) -> outcome(basketRules.judge(lines.stream().map(ScoredBasketLine::toBasketProduct).toList()), language, totalXp));
	}

	/**
	 * The products of the basket that have an Overall Score under the user's Weighting Profile, each once, in the order
	 * first sent. A product that is unknown, not classified, matches no archetype, or whose archetype has no Overall
	 * Score (it is outside the Nutri-Score scheme) is left out. The identifications and the corpus are read once for the
	 * whole basket.
	 */
	private Uni<List<ScoredBasketLine>> scoreBasket(User user, CatalogueId catalogueId, List<ProductKey> productKeys) {
		var distinctKeys = new LinkedHashSet<>(productKeys);
		return forcm(
				personalWeightsProvider.forUser(user),
				_ -> referenceProductIdentifier.identify(catalogueId, distinctKeys),
				(_, _) -> archetypeCorpus.scoredArchetypes(),
				(weights, identifications, archetypes) -> scoredLines(distinctKeys, identifications, archetypes, weights.profile()));
	}

	private static List<ScoredBasketLine> scoredLines(
			Set<ProductKey> productKeys, Map<ProductKey, ProductIdentification> identifications, ScoredArchetypes archetypes, WeightProfile profile) {
		return productKeys.stream()
				.flatMap(productKey -> scoredLine(productKey, identifications.get(productKey), archetypes, profile).stream())
				.toList();
	}

	private static Optional<ScoredBasketLine> scoredLine(
			ProductKey productKey, ProductIdentification identification, ScoredArchetypes archetypes, WeightProfile profile) {
		return switch (identification) {
			case ProductIdentification.Identified identified -> archetypes.find(identified.archetypeProductId())
					.map(scoredProduct -> new ScoredBasketLine(productKey, scoredProduct, scoredProduct.overallScore(profile)));
			case ProductIdentification.NotIdentified _ -> Optional.empty();
		};
	}

	private BasketAssessmentOutcome outcome(BasketVerdict verdict, String language, int totalXp) {
		var basketGrade = verdict.basketGrade();
		return ImmutableBasketAssessmentOutcome.builder()
				.type(basketGrade == null ? NOTHING_TO_ASSESS : ASSESSED)
				.basketGrade(basketGrade)
				.basketGradeLabel(basketGrade == null ? null : basketGradeLabels.labelOf(basketGrade, language))
				.problematicProductKeys(verdict.problematicProductKeys())
				.xpValue(verdict.xpValue())
				.totalXp(totalXp)
				.build();
	}
}
