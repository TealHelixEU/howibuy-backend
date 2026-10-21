package eu.tealhelix.catalogue.dao.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import eu.tealhelix.catalogue.dao.jpa.CatalogueEntity;
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

	static IngestionRunEntity ingestionRun(IngestionRunId id, CatalogueId catalogueId, IngestionRunStatus status, LocalDateTime receivedAt) {
		var entity = new IngestionRunEntity();
		entity.setId(id.asUuid());
		entity.setCatalogueId(catalogueId.asString());
		entity.setFeedType(FeedType.SNAPSHOT);
		entity.setStatus(status);
		entity.setReceivedAt(receivedAt);
		entity.setFilePath(catalogueId.asString() + "/" + id.asString() + ".gz");
		entity.setFileSize(10L);
		entity.setFileSha256("aa");
		return entity;
	}
}
