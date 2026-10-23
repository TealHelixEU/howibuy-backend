package eu.tealhelix.howibuy.services.v1;

import java.util.List;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.services.v1.types.LlmClassification;
import eu.tealhelix.howibuy.v1.model.ClassificationAttemptInput;
import eu.tealhelix.howibuy.v1.model.ClassificationToReview;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
import eu.tealhelix.howibuy.v1.types.HasRetailerProductId;
import io.smallrye.mutiny.Uni;

/**
 * The Classification of the Retailer Products: which of them wait to be classified, the recording of the
 * Classification Attempts that resolve their Classifications, and which Classifications need review.
 */
public interface ClassificationService {
	int MAX_PER_PAGE = 1000;

	/**
	 * Find the products waiting to be classified.
	 *
	 * @param caller The caller, who must be the Catalogue Sync or an operator
	 * @param limit  How many products to find at most, from 1 to {@link #MAX_PER_PAGE}
	 * @return The products, the one whose current state came with the oldest Product Change first
	 */
	Uni<List<PendingProduct>> findPending(User caller, int limit);

	/**
	 * Find the products whose Classification was resolved from attempts that disagree, so one of us should look at it.
	 * A product changed since its Classification was resolved is pending instead.
	 *
	 * @param caller The caller, who must be the Catalogue Sync or an operator
	 * @param limit  How many products to find at most, from 1 to {@link #MAX_PER_PAGE}
	 * @return The products, the one whose Classification was resolved longest ago first
	 */
	Uni<List<ClassificationToReview>> findToReview(User caller, int limit);

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
	 */
	Uni<LlmClassification> classifyWithLlm(User caller, HasRetailerProductId productId, String language);
}
