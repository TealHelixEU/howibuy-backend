package eu.tealhelix.howibuy.dao.impl;

import java.util.UUID;

import eu.tealhelix.howibuy.dao.jpa.CatalogueEntity;
import eu.tealhelix.howibuy.dao.jpa.RetailerEntity;

/**
 * The entities that the DAO tests persist as fixtures, rows the tests need but do not examine.
 */
final class TestEntities {
	private TestEntities() {
	}

	static RetailerEntity retailer(UUID id, String name) {
		var entity = new RetailerEntity();
		entity.setId(id);
		entity.setName(name);
		entity.setActive(true);
		return entity;
	}

	static CatalogueEntity catalogue(String id, UUID retailerId, String country, String primaryLanguage) {
		var entity = new CatalogueEntity();
		entity.setId(id);
		entity.setRetailerId(retailerId);
		entity.setCountry(country);
		entity.setPrimaryLanguage(primaryLanguage);
		entity.setActive(true);
		return entity;
	}
}
