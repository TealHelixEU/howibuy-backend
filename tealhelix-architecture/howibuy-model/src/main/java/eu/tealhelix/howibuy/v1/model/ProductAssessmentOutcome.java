package eu.tealhelix.howibuy.v1.model;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.HasProductKey;
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

	@Nullable
	AlternativeForProduct getBestPersonalAlternative();

	@Nullable
	AlternativeForProduct getBestScientificAlternative();

	@Nullable
	AlternativeForProduct getBestCombinedAlternative();

	/**
	 * Diagnostic data for the assessment, has to be activated explicitly.
	 */
	@Nullable
	ProductAssessmentOutcomeDiagnostics getDiagnostics();
}
