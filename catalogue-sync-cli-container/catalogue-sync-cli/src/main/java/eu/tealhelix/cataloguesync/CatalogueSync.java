package eu.tealhelix.cataloguesync;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import eu.tealhelix.cataloguesync.howibuy.CatalogueChangesRequest;
import eu.tealhelix.cataloguesync.howibuy.CursorConflictException;
import eu.tealhelix.cataloguesync.howibuy.HowiBuyClient;
import eu.tealhelix.cataloguesync.ingestion.IngestionClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Brings HowiBuy up to date with the Ingestion: reads the Product Changes after HowiBuy's cursor a page at a time, and
 * has HowiBuy apply each page, until the Ingestion has no more. HowiBuy keeps the cursor, so the sync keeps no state, and
 * a sync that stopped is resumed by running it again.
 */
@Singleton
public class CatalogueSync {
	private final IngestionClient ingestion;
	private final HowiBuyClient howiBuy;
	private final int pageSize;

	@Inject
	public CatalogueSync(
			@RestClient IngestionClient ingestion,
			@RestClient HowiBuyClient howiBuy,
			@ConfigProperty(name = "catalogue-sync.page-size") int pageSize
	) {
		this.ingestion = ingestion;
		this.howiBuy = howiBuy;
		this.pageSize = pageSize;
	}

	/**
	 * A page that HowiBuy refuses because another sync moved its cursor is read again from the moved cursor; any other
	 * error stops the sync.
	 */
	public SyncResult sync() {
		int pages = 0;
		long products = 0;
		long cursor = readCursor();
		while (true) {
			var page = ingestion.findChanges(cursor, pageSize);
			if (page.products().isEmpty()) {
				return new SyncResult(pages, products, cursor);
			}
			try {
				howiBuy.apply(new CatalogueChangesRequest(cursor, page.toSequence(), page.products()));
				pages++;
				products += page.products().size();
				cursor = page.toSequence();
			} catch (CursorConflictException e) {
				cursor = readCursor();
			}
		}
	}

	private long readCursor() {
		return howiBuy.readCursor().lastSequence();
	}
}
