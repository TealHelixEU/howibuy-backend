package eu.tealhelix.catalogue.dao.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import eu.tealhelix.catalogue.dao.jpa.CatalogueEntity;
import eu.tealhelix.catalogue.dao.jpa.FeedEntity;
import eu.tealhelix.catalogue.dao.jpa.IngestionRunEntity;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.common.v1.types.RetailerId;

/**
 * The entities that the DAO tests persist as fixtures, rows the tests need but do not examine.
 */
final class TestEntities {
	private TestEntities() {
	}

	static CatalogueEntity catalogue(CatalogueId id, RetailerId retailerId, String country, String primaryLanguage, String... otherLanguages) {
		var entity = new CatalogueEntity();
		entity.setId(id.asString());
		entity.setRetailerId(retailerId.asUuid());
		entity.setCountry(country);
		entity.setPrimaryLanguage(primaryLanguage);
		var acceptedLanguages = new ArrayList<>(List.of(primaryLanguage));
		acceptedLanguages.addAll(List.of(otherLanguages));
		entity.setAcceptedLanguages(acceptedLanguages);
		entity.setAdapter("test-adapter");
		entity.setActive(true);
		return entity;
	}

	/**
	 * A run of a Snapshot of its own, which is persisted with it by {@link #withTheirFeeds(IngestionRunEntity...)}.
	 */
	static IngestionRunEntity ingestionRun(IngestionRunId id, CatalogueId catalogueId, RetailerId retailerId, IngestionRunStatus status, LocalDateTime receivedAt) {
		var feed = new FeedEntity();
		feed.setId(UUID.randomUUID());
		feed.setRetailerId(retailerId.asUuid());
		feed.setFeedType(FeedType.SNAPSHOT);
		feed.setReceivedAt(receivedAt);
		feed.setFilePath(retailerId.asString() + "/" + feed.getId() + ".gz");
		feed.setFileSize(10L);
		feed.setFileSha256("aa");
		var entity = new IngestionRunEntity();
		entity.setId(id.asUuid());
		entity.setFeed(feed);
		entity.setCatalogueId(catalogueId.asString());
		entity.setStatus(status);
		return entity;
	}

	/**
	 * @return The runs, each after its Feed, in the order to persist them
	 */
	static Object[] withTheirFeeds(IngestionRunEntity... runs) {
		return Stream.of(runs).flatMap(run -> Stream.of(run.getFeed(), run)).toArray();
	}
}
