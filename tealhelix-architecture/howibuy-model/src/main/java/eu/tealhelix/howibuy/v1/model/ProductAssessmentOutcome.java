package eu.tealhelix.howibuy.v1.model;

import java.util.List;

import eu.tealhelix.catalogue.v1.types.HasProductKey;
import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.IdentificationFailureReason;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.RecommendationType;
import org.immutables.value.Value;

/**
 * Outcome of the sustainability assessment and substitution proposal algorithm for a single product.
 */
@Value.Immutable
public interface ProductAssessmentOutcome extends HasProductKey {
	/**
	 * The overall outcome of the assessment. If the value is {@link ProductAssessmentOutcomeType#SUCCESS},
	 * the other fields will contain the relevant information.
	 */
	ProductAssessmentOutcomeType getType();

	/**
	 * Why the product could not be identified, set when the type is
	 * {@link ProductAssessmentOutcomeType#FAILURE_TO_IDENTIFY}.
	 */
	@Nullable
	IdentificationFailureReason getFailureReason();

	/**
	 * Whether there is something better to buy than the Reference Product, set when the type is
	 * {@link ProductAssessmentOutcomeType#SUCCESS}.
	 */
	@Nullable
	RecommendationType getRecommendation();

	/**
	 * The scientific Overall Score of the Reference Product, set when the type is
	 * {@link ProductAssessmentOutcomeType#SUCCESS} and the recommendation is not {@link RecommendationType#NO_SCORE}.
	 */
	@Nullable
	Double getReferenceOverallScore();

	/**
	 * What to buy in place of the Reference Product, non-empty exactly when the recommendation is
	 * {@link RecommendationType#RECOMMENDED}: up to three, one for each degree of Substitutability, the closest first.
	 */
	List<AlternativeForProduct> getAlternatives();

	/**
	 * How the product was identified, {@code null} when the product is unknown.
	 */
	@Nullable
	IdentificationDiagnostics getDiagnostics();
}
