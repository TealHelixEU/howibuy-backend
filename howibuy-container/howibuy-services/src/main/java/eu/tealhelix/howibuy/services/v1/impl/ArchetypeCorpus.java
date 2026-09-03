package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forcm;

import java.util.concurrent.atomic.AtomicReference;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.howibuy.dao.ArchetypeProductDao;
import eu.tealhelix.howibuy.dao.SubstitutabilityDao;
import eu.tealhelix.howibuy.scoring.v1.SubstitutionSettings;
import io.smallrye.mutiny.Uni;

/**
 * The scored archetype corpus as the assessment flow sees it: read from the database once and kept for the lifetime of
 * the application, since it is reference data that only changes on redeployment.
 * <p>
 * Scoring and normalising the whole corpus is the expensive half of the method, and it is the same for every user, so
 * an assessment costs no query and no normalisation of its own — see ADR 0005.
 */
@ApplicationScoped
public class ArchetypeCorpus {
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final ArchetypeProductDao archetypeProductDao;
	private final SubstitutabilityDao substitutabilityDao;

	/**
	 * Holds the one load, or nothing before the first request asks for it. A load that fails clears itself so the next
	 * request tries again: the alternative is an application that has to be restarted to recover from one unlucky
	 * moment at the database.
	 */
	private final AtomicReference<Uni<ScoredArchetypes>> archetypes = new AtomicReference<>();

	@Inject
	public ArchetypeCorpus(
			ReactivePersistenceContextFactory persistenceContextFactory,
			ArchetypeProductDao archetypeProductDao,
			SubstitutabilityDao substitutabilityDao
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.archetypeProductDao = archetypeProductDao;
		this.substitutabilityDao = substitutabilityDao;
	}

	public Uni<ScoredArchetypes> scoredArchetypes() {
		return archetypes.updateAndGet(loaded -> loaded == null ? loadOnce() : loaded);
	}

	private Uni<ScoredArchetypes> loadOnce() {
		return load()
				.onFailure().invoke(_ -> archetypes.set(null))
				.memoize().indefinitely();
	}

	private Uni<ScoredArchetypes> load() {
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				archetypeProductDao.retrieveAllWithImpacts(em),
				_ -> substitutabilityDao.retrieveAll(em),
				(corpus, matrix) -> ScoredArchetypes.of(corpus, matrix, SubstitutionSettings.defaults())));
	}
}
