package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.dao.ArchetypeProductDao;
import eu.tealhelix.howibuy.dao.ClassificationDao;
import eu.tealhelix.howibuy.services.model.ProductToClassify;
import eu.tealhelix.howibuy.services.v1.ClassificationService;
import eu.tealhelix.howibuy.services.v1.classification.ClassificationResolution;
import eu.tealhelix.howibuy.services.v1.classification.Resolution;
import eu.tealhelix.howibuy.services.v1.types.InputHashConflictException;
import eu.tealhelix.howibuy.v1.model.ClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.ClassificationAttemptData;
import eu.tealhelix.howibuy.v1.model.ImmutableClassification;
import eu.tealhelix.howibuy.v1.model.ImmutableClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
import eu.tealhelix.howibuy.v1.types.Classifier;
import eu.tealhelix.howibuy.v1.types.HasRetailerProductId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class ClassificationServiceImpl implements ClassificationService {
	private final TealHelixAuthorization authorization;
	private final ClassificationDao classificationDao;
	private final ArchetypeProductDao archetypeProductDao;
	private final ClassificationResolution resolution;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	@Inject
	public ClassificationServiceImpl(
			TealHelixAuthorization authorization,
			ClassificationDao classificationDao,
			ArchetypeProductDao archetypeProductDao,
			ClassificationResolution resolution,
			ReactivePersistenceContextFactory persistenceContextFactory
	) {
		this.authorization = authorization;
		this.classificationDao = classificationDao;
		this.archetypeProductDao = archetypeProductDao;
		this.resolution = resolution;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<List<PendingProduct>> findPending(User caller, int limit) {
		authorization.requireCatalogueSyncOrOperator(caller);
		if (limit < 1 || limit > MAX_PENDING_PER_PAGE) {
			throw BadInputValueException.fromInputNameAndHint("limit", "It must be from 1 to " + MAX_PENDING_PER_PAGE);
		}
		return persistenceContextFactory.withoutTransaction(em -> classificationDao.findPending(em, limit));
	}

	@Override
	public Uni<Void> recordAttempt(User caller, HasRetailerProductId productId, ClassificationAttemptData attempt) {
		authorization.requireCatalogueSyncOrOperator(caller);
		if (attempt.getClassifier() == Classifier.LLM) {
			throw BadInputValueException.fromInputNameAndHint("classifier", "The attempts of the LLM are made by HowiBuy itself");
		}
		var recorded = ImmutableClassificationAttempt.builder().from(attempt).createdAt(LocalDateTime.now()).build();
		return persistenceContextFactory.withTransaction(tx -> forc(
				classificationDao.findLockedProductToClassify(tx, productId),
				product -> requireAttemptOf(tx, productId, product, attempt),
				product -> classificationDao.recordAttempt(tx, productId, recorded).replaceWith(product),
				product -> classificationDao.findAttempts(tx, productId, product.getInputHash()).map(attempts -> resolve(product, attempts)),
				resolved -> classificationDao.saveClassification(tx, productId, ImmutableClassification.builder()
						.archetypeProductId(resolved.archetypeProductId())
						.inputHash(attempt.getInputHash())
						.isNeedsReview(resolved.needsReview())
						.resolvedAt(recorded.getCreatedAt())
						.build())
		));
	}

	/**
	 * The input hash is compared before the language, because the names of a product that changed may be others.
	 */
	private Uni<ProductToClassify> requireAttemptOf(ReactivePersistenceContext em, HasRetailerProductId productId, ProductToClassify product, ClassificationAttemptData attempt) {
		if (product == null) {
			return Uni.createFrom().failure(new NotFoundException(productId.getId(), "No such Retailer Product, retailerProductId: " + productId.getId().asString()));
		} else if (!product.getInputHash().equals(attempt.getInputHash())) {
			return Uni.createFrom().failure(new InputHashConflictException(productId, attempt.getInputHash()));
		} else if (!product.getLanguages().contains(attempt.getLanguage())) {
			return Uni.createFrom().failure(BadInputValueException.fromInputNameAndHint("language", "The product is not named in it, language: " + attempt.getLanguage()));
		} else if (attempt.getArchetypeProductId() == null) {
			return Uni.createFrom().item(product);
		} else {
			return archetypeProductDao.requireProductExists(em, attempt.getArchetypeProductId()).replaceWith(product);
		}
	}

	/**
	 * The attempt just recorded was made from the current input hash, so there is always a Classification to resolve.
	 */
	private Resolution resolve(ProductToClassify product, List<ClassificationAttempt> attempts) {
		return resolution.resolve(product.getInputHash(), product.getClassificationLanguage(), attempts)
				.orElseThrow(() -> new IllegalStateException("No attempt was made from the current input hash, inputHash: " + product.getInputHash()));
	}
}
