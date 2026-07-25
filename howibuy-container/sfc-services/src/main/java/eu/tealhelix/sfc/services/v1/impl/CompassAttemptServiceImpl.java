package eu.tealhelix.sfc.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.sfc.dao.AnswerDao;
import eu.tealhelix.sfc.dao.AttemptDao;
import eu.tealhelix.sfc.services.v1.CompassAttemptService;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class CompassAttemptServiceImpl implements CompassAttemptService {
	private final TealHelixAuthorization authorization;
	private final AttemptDao attemptDao;
	private final AnswerDao answerDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	@Inject
	public CompassAttemptServiceImpl(
			TealHelixAuthorization authorization,
			AttemptDao attemptDao,
			AnswerDao answerDao,
			ReactivePersistenceContextFactory persistenceContextFactory
	) {
		this.authorization = authorization;
		this.attemptDao = attemptDao;
		this.answerDao = answerDao;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<Void> answer(User user, UUID questionId, ScaleOption option) {
		authorization.requireUserNotService(user);
		if (option == null) {
			throw RequiredInputMissingException.fromRequiredInputName("option");
		}
		return persistenceContextFactory.withTransaction(tx -> answerInTx(tx, user, questionId, option));
	}

	private Uni<Void> answerInTx(ReactivePersistenceTxContext tx, User user, UUID questionId, ScaleOption option) {
		UUID userId = user.getId().asUuid();
		return forc(
				attemptDao.findInProgressId(tx, userId),
				startInProgressIfNotFound(tx, userId),
				attemptId -> answerDao.upsert(tx, attemptId, questionId, option)
		);
	}

	private Function<Optional<UUID>, Uni<? extends UUID>> startInProgressIfNotFound(ReactivePersistenceTxContext tx, UUID userId) {
		return inProgressOptional -> inProgressOptional
				.map(id -> Uni.createFrom().item(id))
				.orElseGet(() -> attemptDao.startInProgress(tx, userId));
	}
}
