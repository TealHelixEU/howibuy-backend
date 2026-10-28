package eu.tealhelix.catalogue.v1.model;

import java.time.LocalDateTime;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.HasIngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.common.types.Nullable;
import org.immutables.value.Value;

/**
 * The processing of one Feed for one Catalogue, recorded so problems can be traced to a file and an adapter.
 */
@Value.Immutable
public interface IngestionRun extends HasIngestionRunId {
	@Override
	IngestionRunId getId();

	/**
	 * The Feed the run processes, which tells its type, its receipt and its file.
	 */
	Feed getFeed();

	CatalogueId getCatalogueId();

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

	@Nullable
	LocalDateTime getStartedAt();

	@Nullable
	LocalDateTime getFinishedAt();

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

	/**
	 * The run whose stored Feed this run processes again; {@code null} if this run received its Feed itself. A replay
	 * has the {@linkplain #getFeed() Feed} of the run it replays.
	 */
	@Nullable
	IngestionRunId getReplayOfRunId();
}
