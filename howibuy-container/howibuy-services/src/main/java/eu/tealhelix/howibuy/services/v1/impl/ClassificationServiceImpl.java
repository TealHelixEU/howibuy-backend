package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
import eu.tealhelix.howibuy.services.model.ProductClassificationInput;
import eu.tealhelix.howibuy.services.model.ProductToClassify;
import eu.tealhelix.howibuy.services.v1.ClassificationService;
import eu.tealhelix.howibuy.services.v1.classification.ClassificationResolution;
import eu.tealhelix.howibuy.services.v1.classification.Resolution;
import eu.tealhelix.howibuy.services.v1.types.ArchetypeMatchOutcome;
import eu.tealhelix.howibuy.services.v1.types.InputHashConflictException;
import eu.tealhelix.howibuy.services.v1.types.LlmClassification;
import eu.tealhelix.howibuy.v1.model.Classification;
import eu.tealhelix.howibuy.v1.model.ClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.ClassificationAttemptData;
import eu.tealhelix.howibuy.v1.model.ClassificationAttemptInput;
import eu.tealhelix.howibuy.v1.model.ClassificationToReview;
import eu.tealhelix.howibuy.v1.model.ImmutableClassification;
import eu.tealhelix.howibuy.v1.model.ImmutableClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.MatchDiagnostics;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.Classifier;
import eu.tealhelix.howibuy.v1.types.HasRetailerProductId;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ClassificationServiceImpl implements ClassificationService {
	public static final String LLM_VERSION_KEY = "howibuy.classification.llm-version";

	private final TealHelixAuthorization authorization;
	private final ClassificationDao classificationDao;
	private final ArchetypeProductDao archetypeProductDao;
	private final ClassificationResolution resolution;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final ArchetypeMatching archetypeMatching;
	private final String llmVersion;

	@Inject
	public ClassificationServiceImpl(
			TealHelixAuthorization authorization,
			ClassificationDao classificationDao,
			ArchetypeProductDao archetypeProductDao,
			ClassificationResolution resolution,
			ReactivePersistenceContextFactory persistenceContextFactory,
			ArchetypeMatching archetypeMatching,
			@ConfigProperty(name = LLM_VERSION_KEY) String llmVersion
	) {
		this.authorization = authorization;
		this.classificationDao = classificationDao;
		this.archetypeProductDao = archetypeProductDao;
		this.resolution = resolution;
		this.persistenceContextFactory = persistenceContextFactory;
		this.archetypeMatching = archetypeMatching;
		this.llmVersion = llmVersion;
	}

	@Override
	public Uni<List<PendingProduct>> findPending(User caller, int limit) {
		authorization.requireCatalogueSyncOrOperator(caller);
		requireLimitOfPage(limit);
		return persistenceContextFactory.withoutTransaction(em -> classificationDao.findPending(em, limit));
	}

	@Override
	public Uni<List<ClassificationToReview>> findToReview(User caller, int limit) {
		authorization.requireCatalogueSyncOrOperator(caller);
		requireLimitOfPage(limit);
		return persistenceContextFactory.withoutTransaction(em -> classificationDao.findToReview(em, limit));
	}

	@Override
	public Uni<Void> recordAttempt(User caller, HasRetailerProductId productId, ClassificationAttemptInput attempt) {
		authorization.requireCatalogueSyncOrOperator(caller);
		if (attempt.getClassifier() == Classifier.LLM) {
			throw BadInputValueException.fromInputNameAndHint("classifier", "The attempts of the LLM are made by HowiBuy itself");
		}
		var recorded = ImmutableClassificationAttempt.builder().from(attempt).createdAt(LocalDateTime.now()).build();
		return recordAndResolve(productId, recorded).replaceWithVoid();
	}

	/**
	 * The product is read and matched outside any transaction, because the LLM takes long; the input hash it was read
	 * with tells, once the product is locked, whether it changed in the meantime.
	 */
	@Override
	public Uni<LlmClassification> classifyWithLlm(User caller, HasRetailerProductId productId, String language) {
		authorization.requireCatalogueSyncOrOperator(caller);
		return forc(
				persistenceContextFactory.withoutTransaction(em -> classificationDao.findClassificationInput(em, productId)),
				input -> requireNamedIn(productId, input, language),
				input -> archetypeMatching.match(TrimmedProductBaseData.of(language, input.getNames().get(language), input.getCharacteristics(), input.getTags())),
				(input, outcome) -> switch (outcome) {
					case ArchetypeMatchOutcome.Matched matched -> recordLlmAttempt(productId, language, input.getInputHash(), matched.archetypeProductId(), matched.diagnostics());
					case ArchetypeMatchOutcome.NoMatch noMatch -> recordLlmAttempt(productId, language, input.getInputHash(), null, noMatch.diagnostics());
					case ArchetypeMatchOutcome.Malformed malformed -> Uni.createFrom().<LlmClassification>item(new LlmClassification.Malformed(malformed.diagnostics()));
				}
		);
	}

	@Override
	public Uni<ArchetypeMatchOutcome> previewWithLlm(User caller, String language, String name, Map<String, String> characteristics, List<String> tags) {
		authorization.requireOperator(caller);
		var product = TrimmedProductBaseData.of(language, name, characteristics, tags);
		if (product.getName().isEmpty()) {
			throw BadInputValueException.fromInputNameAndHint("name", "It must not be blank");
		}
		return archetypeMatching.match(product);
	}

	private Uni<LlmClassification> recordLlmAttempt(HasRetailerProductId productId, String language, String inputHash, ArchetypeProductId archetypeProductId, MatchDiagnostics diagnostics) {
		var attempt = ImmutableClassificationAttempt.builder()
				.language(language)
				.classifier(Classifier.LLM)
				.classifierVersion(llmVersion)
				.archetypeProductId(archetypeProductId)
				.inputHash(inputHash)
				.diagnostics(diagnostics)
				.createdAt(LocalDateTime.now())
				.build();
		return recordAndResolve(productId, attempt).map(classification -> new LlmClassification.Recorded(attempt, classification));
	}

	/**
	 * Record an attempt and resolve the product's Classification from its attempts, in one transaction with the product
	 * locked.
	 *
	 * @return The Classification resolved
	 */
	private Uni<Classification> recordAndResolve(HasRetailerProductId productId, ClassificationAttempt attempt) {
		return persistenceContextFactory.withTransaction(tx -> forc(
				classificationDao.findLockedProductToClassify(tx, productId),
				product -> requireAttemptOf(tx, productId, product, attempt),
				product -> classificationDao.recordAttempt(tx, productId, attempt).replaceWith(product),
				product -> classificationDao.findAttempts(tx, productId, product.getInputHash()).map(attempts -> resolve(product, attempts)),
				resolved -> {
					Classification classification = ImmutableClassification.builder()
							.archetypeProductId(resolved.archetypeProductId())
							.inputHash(attempt.getInputHash())
							.isNeedsReview(resolved.needsReview())
							.resolvedAt(attempt.getCreatedAt())
							.build();
					return classificationDao.saveClassification(tx, productId, classification).replaceWith(classification);
				}
		));
	}

	private static void requireLimitOfPage(int limit) {
		if (limit < 1 || limit > MAX_PER_PAGE) {
			throw BadInputValueException.fromInputNameAndHint("limit", "It must be from 1 to " + MAX_PER_PAGE);
		}
	}

	/**
	 * The input hash is compared before the language, because the names of a product that changed may be others.
	 */
	private Uni<ProductToClassify> requireAttemptOf(ReactivePersistenceContext em, HasRetailerProductId productId, ProductToClassify product, ClassificationAttemptData attempt) {
		if (product == null) {
			return Uni.createFrom().failure(noSuchProduct(productId));
		} else if (!product.getInputHash().equals(attempt.getInputHash())) {
			return Uni.createFrom().failure(new InputHashConflictException(productId, attempt.getInputHash()));
		} else if (!product.getLanguages().contains(attempt.getLanguage())) {
			return Uni.createFrom().failure(notNamedIn(attempt.getLanguage()));
		} else if (attempt.getArchetypeProductId() == null) {
			return Uni.createFrom().item(product);
		} else {
			return archetypeProductDao.requireProductExists(em, attempt.getArchetypeProductId()).replaceWith(product);
		}
	}

	private static Uni<ProductClassificationInput> requireNamedIn(HasRetailerProductId productId, ProductClassificationInput input, String language) {
		if (input == null) {
			return Uni.createFrom().failure(noSuchProduct(productId));
		} else if (!input.getNames().containsKey(language)) {
			return Uni.createFrom().failure(notNamedIn(language));
		} else {
			return Uni.createFrom().item(input);
		}
	}

	private static NotFoundException noSuchProduct(HasRetailerProductId productId) {
		return new NotFoundException(productId.getId(), "No such Retailer Product, retailerProductId: " + productId.getId().asString());
	}

	private static BadInputValueException notNamedIn(String language) {
		return BadInputValueException.fromInputNameAndHint("language", "The product is not named in it, language: " + language);
	}

	/**
	 * The attempt just recorded was made from the current input hash, so there is always a Classification to resolve.
	 */
	private Resolution resolve(ProductToClassify product, List<ClassificationAttempt> attempts) {
		return resolution.resolve(product.getInputHash(), product.getClassificationLanguage(), attempts)
				.orElseThrow(() -> new IllegalStateException("No attempt was made from the current input hash, inputHash: " + product.getInputHash()));
	}
}
