package eu.tealhelix.howibuy.v1.model;

import eu.tealhelix.catalogue.v1.types.HasProductKey;
import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.IdentificationFailureReason;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
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

	@Nullable
	AlternativeForProduct getBestPersonalAlternative();

	@Nullable
	AlternativeForProduct getBestScientificAlternative();

	@Nullable
	AlternativeForProduct getBestCombinedAlternative();

	/**
	 * How the product was identified, {@code null} when the product is unknown.
	 */
	@Nullable
	IdentificationDiagnostics getDiagnostics();
}
