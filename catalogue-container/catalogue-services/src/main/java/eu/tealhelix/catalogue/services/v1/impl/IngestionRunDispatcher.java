package eu.tealhelix.catalogue.services.v1.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts the processing of the Ingestion Runs waiting for it, on the executor. The runs of one Catalogue are processed one
 * at a time, in order of receipt, while those of different Catalogues may be processed in parallel. It keeps track of the
 * Catalogues being processed in memory, so it assumes a single instance of the Ingestion.
 */
@ApplicationScoped
public class IngestionRunDispatcher {
	private static final Logger LOG = LoggerFactory.getLogger(IngestionRunDispatcher.class);

	private final IngestionRunDao ingestionRunDao;
	private final IngestionRunProcessor processor;
	private final UniAwaiter uniAwaiter;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final ManagedExecutor executor;

	/**
	 * How many times each Catalogue being processed was woken up. The processing stops only when no wake-up arrived since
	 * it last looked for runs, so a wake-up is never lost.
	 */
	private final ConcurrentMap<CatalogueId, Integer> wakeUps = new ConcurrentHashMap<>();

	@Inject
	public IngestionRunDispatcher(
			IngestionRunDao ingestionRunDao,
			IngestionRunProcessor processor,
			UniAwaiter uniAwaiter,
			ReactivePersistenceContextFactory persistenceContextFactory,
			ManagedExecutor executor
	) {
		this.ingestionRunDao = ingestionRunDao;
		this.processor = processor;
		this.uniAwaiter = uniAwaiter;
		this.persistenceContextFactory = persistenceContextFactory;
		this.executor = executor;
	}

	/**
	 * Have the runs of a Catalogue that wait to be processed processed. It returns at once.
	 *
	 * @param catalogueId The Catalogue
	 */
	public void wake(CatalogueId catalogueId) {
		if (wakeUps.merge(catalogueId, 1, Integer::sum) == 1) {
			executor.execute(() -> processWaitingRuns(catalogueId));
		}
	}

	/**
	 * Have the runs that a stop left {@link eu.tealhelix.catalogue.v1.types.IngestionRunStatus#PROCESSING PROCESSING}
	 * processed again, along with those that wait. Called once, when the Ingestion starts. It blocks until the runs are
	 * reset, so it must be called where blocking is allowed.
	 */
	public void recover() {
		uniAwaiter.await(() -> persistenceContextFactory.withTransaction(ingestionRunDao::resetProcessing));
		uniAwaiter.await(() -> persistenceContextFactory.withoutTransaction(ingestionRunDao::findCataloguesWithReceivedRuns))
				.forEach(this::wake);
	}

	private void processWaitingRuns(CatalogueId catalogueId) {
		try {
			int seen;
			do {
				seen = wakeUps.get(catalogueId);
				IngestionRun run;
				while ((run = findNextToProcess(catalogueId)) != null) {
					processor.process(run);
				}
			} while (!wakeUps.remove(catalogueId, seen));
		} catch (RuntimeException e) {
			wakeUps.remove(catalogueId);
			LOG.error("Processing the Ingestion Runs of a Catalogue failed, catalogueId: " + catalogueId.asString(), e);
		}
	}

	private IngestionRun findNextToProcess(CatalogueId catalogueId) {
		return uniAwaiter.await(() -> persistenceContextFactory.withoutTransaction(em -> ingestionRunDao.findNextToProcess(em, catalogueId)));
	}
}
