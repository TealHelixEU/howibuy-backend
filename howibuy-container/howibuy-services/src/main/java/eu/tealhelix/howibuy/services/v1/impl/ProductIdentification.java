package eu.tealhelix.howibuy.services.v1.impl;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.model.IdentificationDiagnostics;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.IdentificationFailureReason;

/**
 * Which Archetype Product a scanned product is, as {@link ReferenceProductIdentifier} looks it up, or why it cannot
 * tell.
 */
public sealed interface ProductIdentification {
	/**
	 * How the product was identified, {@code null} when the product is unknown.
	 */
	@Nullable
	IdentificationDiagnostics diagnostics();

	record Identified(ArchetypeProductId archetypeProductId, IdentificationDiagnostics diagnostics) implements ProductIdentification {
	}

	record NotIdentified(IdentificationFailureReason reason, @Nullable IdentificationDiagnostics diagnostics) implements ProductIdentification {
	}
}
