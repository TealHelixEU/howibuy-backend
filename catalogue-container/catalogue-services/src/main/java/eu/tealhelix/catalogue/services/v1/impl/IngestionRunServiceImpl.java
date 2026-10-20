package eu.tealhelix.catalogue.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.dao.CatalogueDao;
import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.services.v1.IngestionRunService;
import eu.tealhelix.catalogue.v1.model.IngestionReject;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.IngestionRunId;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.entity.NotFoundException;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.v1.model.User;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class IngestionRunServiceImpl implements IngestionRunService {
	private final TealHelixAuthorization authorization;
	private final CatalogueDao catalogueDao;
	private final IngestionRunDao ingestionRunDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	@Inject
	public IngestionRunServiceImpl(
			TealHelixAuthorization authorization,
			CatalogueDao catalogueDao,
			IngestionRunDao ingestionRunDao,
			ReactivePersistenceContextFactory persistenceContextFactory
	) {
		this.authorization = authorization;
		this.catalogueDao = catalogueDao;
		this.ingestionRunDao = ingestionRunDao;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<IngestionRun> find(User caller, IngestionRunId runId) {
		authorization.requireLogin(caller);
		return persistenceContextFactory.withoutTransaction(em -> requireReadableRun(em, caller, runId));
	}

	@Override
	public Uni<List<IngestionReject>> findRejects(User caller, IngestionRunId runId, int offset, int limit) {
		authorization.requireLogin(caller);
		requirePage(offset, limit);
		return persistenceContextFactory.withoutTransaction(em -> forc(
				requireReadableRun(em, caller, runId),
				run -> ingestionRunDao.findRejects(em, runId, offset, limit)
		));
	}

	private static void requirePage(int offset, int limit) {
		if (offset < 0) {
			throw BadInputValueException.fromInputNameAndHint("offset", "It must not be negative");
		}
		if (limit < 1 || limit > MAX_REJECTS_PER_PAGE) {
			throw BadInputValueException.fromInputNameAndHint("limit", "It must be from 1 to " + MAX_REJECTS_PER_PAGE);
		}
	}

	/**
	 * An operator reads every run; a Retailer reads the runs of its own Catalogues.
	 */
	private Uni<IngestionRun> requireReadableRun(ReactivePersistenceContext em, User caller, IngestionRunId runId) {
		return ingestionRunDao.find(em, runId).flatMap(run -> {
			if (run == null) {
				return Uni.createFrom().failure(new NotFoundException(runId, "No such Ingestion Run, runId: " + runId.asString()));
			} else if (caller.isOperator()) {
				return Uni.createFrom().item(run);
			} else if (!caller.isRetailer()) {
				return Uni.createFrom().failure(new NotAuthorizedException("Only Retailers and operators read Ingestion Runs"));
			} else {
				return catalogueDao.find(em, run.getCatalogueId()).flatMap(catalogue ->
						catalogue.getRetailerId().asUuid().equals(caller.getId().asUuid())
								? Uni.createFrom().item(run)
								: Uni.createFrom().failure(new NotAuthorizedException("The Ingestion Run belongs to another Retailer, runId: " + runId.asString())));
			}
		});
	}
}
