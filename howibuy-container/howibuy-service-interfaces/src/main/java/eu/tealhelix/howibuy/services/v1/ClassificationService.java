package eu.tealhelix.howibuy.services.v1;

import java.util.List;
import java.util.Map;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.services.v1.types.ArchetypeMatchOutcome;
import eu.tealhelix.howibuy.services.v1.types.LlmClassification;
import eu.tealhelix.howibuy.v1.model.ClassificationAttemptInput;
import eu.tealhelix.howibuy.v1.model.ClassificationToReview;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
import eu.tealhelix.howibuy.v1.types.Classifier;
import eu.tealhelix.howibuy.v1.types.HasRetailerProductId;
import io.smallrye.mutiny.Uni;

/**
 * The Classification of the Retailer Products: which of them wait to be classified, the recording of the
 * Classification Attempts that resolve their Classifications, which Classifications need review, and the reopening of
 * those a classifier is to make again.
 */
public interface ClassificationService {
	int MAX_PER_PAGE = 1000;

	/**
	 * Find the products waiting to be classified.
	 *
	 * @param caller The caller, who must be the Catalogue Sync or an operator
	 * @param limit  How many products to find at most, from 1 to {@link #MAX_PER_PAGE}
	 * @return The products, the one whose current state came with the oldest Product Change first
	 * @throws eu.tealhelix.common.types.validation.RequiredInputMissingException without a limit
	 * @throws eu.tealhelix.common.types.validation.BadInputValueException       if the limit is out of its range
	 */
	Uni<List<PendingProduct>> findPending(User caller, Integer limit);

	/**
	 * Find the products whose Classification was resolved from attempts that disagree, so one of us should look at it.
	 * A product changed since its Classification was resolved, or whose Classification was reopened, is pending instead.
	 *
	 * @param caller The caller, who must be the Catalogue Sync or an operator
	 * @param limit  How many products to find at most, from 1 to {@link #MAX_PER_PAGE}
	 * @return The products, the one whose Classification was resolved longest ago first
	 * @throws eu.tealhelix.common.types.validation.RequiredInputMissingException without a limit
	 * @throws eu.tealhelix.common.types.validation.BadInputValueException       if the limit is out of its range
	 */
	Uni<List<ClassificationToReview>> findToReview(User caller, Integer limit);

	/**
	 * Record an attempt made outside HowiBuy, by an expert or one of us, and resolve the product's Classification from
	 * its attempts. The attempts recorded for one product at the same time resolve one after the other.
	 *
	 * @param caller    The caller, who must be the Catalogue Sync or an operator
	 * @param productId The product classified
	 * @param attempt   The attempt, made from the product's name in one of its languages; not by the LLM, whose
	 *                  attempts HowiBuy makes itself
	 * @return A {@code Uni} completing when the attempt has been recorded and the Classification resolved, or failing
	 *         with a {@link eu.tealhelix.common.types.entity.NotFoundException} if the product or the archetype is
	 *         unknown, or with an {@link eu.tealhelix.howibuy.services.v1.types.InputHashConflictException} if the
	 *         attempt was made from another input hash than the product's current one
	 * @throws eu.tealhelix.common.types.validation.RequiredInputMissingException without a product
	 */
	Uni<Void> recordAttempt(User caller, HasRetailerProductId productId, ClassificationAttemptInput attempt);

	/**
	 * Classify a product with the LLM, from its name in one language, its characteristics and its tags, and, unless the
	 * reply of the LLM is malformed, record the attempt and resolve the product's Classification from its attempts. The
	 * product is not locked while the LLM works.
	 *
	 * @param caller    The caller, who must be the Catalogue Sync or an operator
	 * @param productId The product to classify
	 * @param language  The language of the name to classify the product from
	 * @return How the LLM classified the product, or a failure with a
	 *         {@link eu.tealhelix.common.types.entity.NotFoundException} if the product is unknown, with a
	 *         {@link eu.tealhelix.common.types.validation.BadInputValueException} if it is not named in the language, or
	 *         with an {@link eu.tealhelix.howibuy.services.v1.types.InputHashConflictException} if it changed while the
	 *         LLM worked
	 * @throws eu.tealhelix.common.types.validation.RequiredInputMissingException without a product or a language
	 */
	Uni<LlmClassification> classifyWithLlm(User caller, HasRetailerProductId productId, String language);

	/**
	 * Match product data to an Archetype Product with the LLM, as {@link #classifyWithLlm} matches a stored product,
	 * storing nothing.
	 *
	 * @param caller          The caller, who must be an operator
	 * @param language        The language the product data is written in
	 * @param name            The name of the product
	 * @param characteristics The characteristics of the product, none if {@code null}
	 * @param tags            The tags of the product, none if {@code null}
	 * @return How the matching ended
	 * @throws eu.tealhelix.common.types.validation.RequiredInputMissingException without a language or a name
	 * @throws eu.tealhelix.common.types.validation.BadInputValueException       if the name is blank, a characteristic has no value, or a tag
	 *                                                                     is {@code null}
	 */
	Uni<ArchetypeMatchOutcome> previewWithLlm(User caller, String language, String name, Map<String, String> characteristics, List<String> tags);

	/**
	 * Reopen the Classifications that came from a classifier version, after the classifier improved, which makes their
	 * products pending. A reopened Classification still tells the archetype of its product until an attempt resolves it
	 * again. A Classification came from the versions of the latest attempts, one per language, of the classifier that
	 * decided it.
	 *
	 * @param caller            The caller, who must be an operator
	 * @param classifier        The classifier
	 * @param classifierVersion The version of the classifier
	 * @return How many Classifications were reopened
	 * @throws eu.tealhelix.common.types.validation.RequiredInputMissingException without a classifier or its version
	 */
	Uni<Integer> reopen(User caller, Classifier classifier, String classifierVersion);
}
