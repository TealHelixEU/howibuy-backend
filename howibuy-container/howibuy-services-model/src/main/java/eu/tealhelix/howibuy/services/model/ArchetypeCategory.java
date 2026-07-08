package eu.tealhelix.howibuy.services.model;

import java.util.UUID;

import org.immutables.value.Value;

/**
 * A node of the SAFAD taxonomy tree, carried between the DAO and service layers as the product assessment algorithm
 * descends the category hierarchy one level at a time. The {@link #getId() id} identifies the node so its children can
 * be fetched; the {@link #getName() name} is the candidate shown to the AI when it picks the matching subcategory.
 */
@Value.Immutable
public interface ArchetypeCategory {
	UUID getId();

	String getName();
}
