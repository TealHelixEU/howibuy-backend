package eu.tealhelix.howibuy.services.model;

import java.util.UUID;

import org.immutables.value.Value;

/**
 * A leaf archetype product of the SAFAD taxonomy, carried between the DAO and service layers as the last step of the
 * product assessment descent. The {@link #getName() name} is the candidate shown to the AI when it picks the archetype
 * that best matches the assessed product; the {@link #getId() id} identifies it for the subsequent impact lookup.
 */
@Value.Immutable
public interface ArchetypeProduct {
	UUID getId();

	String getName();
}
