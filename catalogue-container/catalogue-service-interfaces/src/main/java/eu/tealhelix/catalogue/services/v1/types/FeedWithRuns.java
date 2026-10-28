package eu.tealhelix.catalogue.services.v1.types;

import java.util.List;

import eu.tealhelix.catalogue.v1.model.Feed;
import eu.tealhelix.catalogue.v1.model.IngestionRun;

/**
 * A Feed and the Ingestion Runs that process it, one per Catalogue it is for and one per replay.
 *
 * @param feed The Feed
 * @param runs Its runs, ordered by Catalogue and then by when they started, the runs not yet started last
 */
public record FeedWithRuns(Feed feed, List<IngestionRun> runs) {
}
