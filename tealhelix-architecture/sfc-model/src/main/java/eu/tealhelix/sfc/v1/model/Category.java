package eu.tealhelix.sfc.v1.model;

import java.util.UUID;

import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;
import org.immutables.value.Value;

/**
 * A compass category, addressing one {@link SustainabilityDimension}, with its human-facing text resolved for a single
 * requested language. The {@link #getId() id} identifies the category so its questions can be fetched; the
 * {@link #getName() name} and {@link #getDescription() description} are the localized content served to the user. The
 * two links are optional — they are absent until their content has been authored.
 */
@Value.Immutable
public interface Category {
	UUID getId();

	SustainabilityDimension getDimension();

	String getName();

	/**
	 * The rich-text description of the category, in the requested language.
	 */
	String getDescription();

	/**
	 * The link to the category's introductory video, in the requested language, or {@code null} if none has been
	 * authored yet.
	 */
	@Nullable
	String getVideoUrl();

	/**
	 * The link to the category's detailed description, in the requested language, or {@code null} if none has been
	 * authored yet.
	 */
	@Nullable
	String getDetailUrl();
}
