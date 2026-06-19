package eu.tealhelix.howibuy.v1.model;

import eu.tealhelix.common.types.Nullable;
import org.immutables.value.Value;

@Value.Immutable
public interface ProductAssessmentOutcomeDiagnostics {
	@Nullable
	String getL1Category();

	@Nullable
	String getL2Category();

	@Nullable
	String getL3Category();

	@Nullable
	String getProduct();
}
