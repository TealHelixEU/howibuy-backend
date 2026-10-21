package eu.tealhelix.catalogue.v1.model;

import java.time.LocalDateTime;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.common.types.Nullable;
import org.immutables.value.Value;

/**
 * The receipt and processing of one Feed of a Catalogue, recorded so problems can be traced to a file and an adapter.
 */
@Value.Immutable
public interface IngestionRun {
	IngestionRunId getId();

	CatalogueId getCatalogueId();

	FeedType getFeedType();

	IngestionRunStatus getStatus();

	/**
	 * The adapter that parses the Feed; {@code null} until processing starts.
	 */
	@Nullable
	String getAdapter();

	/**
	 * The version of the {@linkplain #getAdapter() adapter}; {@code null} until processing starts.
	 */
	@Nullable
	String getAdapterVersion();

	LocalDateTime getReceivedAt();

	@Nullable
	LocalDateTime getStartedAt();

	@Nullable
	LocalDateTime getFinishedAt();

	/**
	 * Where the Feed is stored, gzip-compressed, relative to the directory of the feed store.
	 */
	String getFilePath();

	/**
	 * The size of the Feed as received, before compression, in bytes.
	 */
	long getFileSize();

	/**
	 * The SHA-256 of the Feed as received, before compression, in lowercase hex.
	 */
	String getFileSha256();

	/**
	 * What the processing found and did; {@code null} until the whole Feed is applied.
	 */
	@Nullable
	IngestionRunCounts getCounts();

	/**
	 * Why the run {@linkplain IngestionRunStatus#FAILED failed}; {@code null} otherwise.
	 */
	@Nullable
	String getError();
}
