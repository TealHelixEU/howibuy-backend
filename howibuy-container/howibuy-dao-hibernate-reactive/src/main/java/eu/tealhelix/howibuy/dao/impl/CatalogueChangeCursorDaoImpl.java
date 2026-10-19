package eu.tealhelix.howibuy.dao.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.howibuy.dao.CatalogueChangeCursorDao;
import eu.tealhelix.howibuy.dao.jpa.CatalogueChangeCursorEntity;
import eu.tealhelix.howibuy.dao.jpa.CatalogueChangeCursorEntity_;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class CatalogueChangeCursorDaoImpl implements CatalogueChangeCursorDao {
	/**
	 * The one Ingestion the changes come from, whose cursor row the schema seeds.
	 */
	private static final String SOURCE = "ingestion";

	@Override
	public Uni<Long> requireLastSequence(ReactivePersistenceContext em) {
		return em.find(CatalogueChangeCursorEntity.class, SOURCE)
				.onItem().ifNull().failWith(() -> new IllegalStateException("The change cursor is missing, source: " + SOURCE))
				.map(CatalogueChangeCursorEntity::getLastSequence);
	}

	/**
	 * One statement carries the whole decision, so that two syncs applying pages from the same sequence cannot both
	 * pass it: the second one waits for the first to commit, reads the row again, and no longer matches it. Reading the
	 * cursor first and updating it afterwards would let both of them read the same sequence.
	 */
	@Override
	public Uni<Boolean> advance(ReactivePersistenceTxContext tx, long fromSequence, long toSequence) {
		var cb = tx.getCriteriaBuilder();
		var u = cb.createCriteriaUpdate(CatalogueChangeCursorEntity.class);
		var cursor = u.from(CatalogueChangeCursorEntity.class);
		u.set(cursor.get(CatalogueChangeCursorEntity_.lastSequence), toSequence);
		u.where(
				cb.equal(cursor.get(CatalogueChangeCursorEntity_.source), SOURCE),
				cb.equal(cursor.get(CatalogueChangeCursorEntity_.lastSequence), fromSequence)
		);
		return tx.createUpdate(u).execute().map(updated -> updated == 1);
	}
}
