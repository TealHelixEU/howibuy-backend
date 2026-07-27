package eu.tealhelix.sfc.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.sfc.dao.AnswerDao;
import eu.tealhelix.sfc.dao.AttemptDao;
import eu.tealhelix.sfc.dao.QuestionDao;
import eu.tealhelix.sfc.services.v1.CompassAttemptService;
import eu.tealhelix.sfc.services.v1.types.IncompleteCompassAttemptException;
import eu.tealhelix.sfc.services.v1.types.NoInProgressAttemptException;
import eu.tealhelix.sfc.services.v1.types.StabilityWindowActiveException;
import eu.tealhelix.sfc.v1.types.QuestionId;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class CompassAttemptServiceImpl implements CompassAttemptService {
	private final TealHelixAuthorization authorization;
	private final AttemptDao attemptDao;
	private final AnswerDao answerDao;
	private final QuestionDao questionDao;
	private final DateTimeService dateTimeService;
	private final StabilityWindow stabilityWindow;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	@Inject
	public CompassAttemptServiceImpl(
			TealHelixAuthorization authorization,
			AttemptDao attemptDao,
			AnswerDao answerDao,
			QuestionDao questionDao,
			DateTimeService dateTimeService,
			StabilityWindow stabilityWindow,
			ReactivePersistenceContextFactory persistenceContextFactory
	) {
		this.authorization = authorization;
		this.attemptDao = attemptDao;
		this.answerDao = answerDao;
		this.questionDao = questionDao;
		this.dateTimeService = dateTimeService;
		this.stabilityWindow = stabilityWindow;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<Void> answer(User user, QuestionId questionId, ScaleOption option) {
		authorization.requireUserNotService(user);
		if (option == null) {
			throw RequiredInputMissingException.fromRequiredInputName("option");
		}
		return persistenceContextFactory.withTransaction(tx -> answerInTx(tx, user.getId().asUuid(), questionId, option));
	}

	private Uni<Void> answerInTx(ReactivePersistenceTxContext tx, UUID userId, QuestionId questionId, ScaleOption option) {
		return forc(
				attemptDao.findInProgressId(tx, userId),
				inProgress -> writableAttempt(tx, userId, inProgress),
				attemptId -> answerDao.upsert(tx, attemptId, questionId, option));
	}

	/**
	 * The attempt an answer should be written to: the in-progress one if there is any, otherwise a freshly started
	 * attempt.
	 */
	private Uni<UUID> writableAttempt(ReactivePersistenceTxContext tx, UUID userId, Optional<UUID> inProgress) {
		return inProgress
				.map(attemptId -> Uni.createFrom().item(attemptId))
				.orElseGet(() -> startEligibleAttempt(tx, userId));
	}

	/**
	 * Starts a fresh attempt, but only once any prior attempt's stability window has elapsed; within that window
	 * starting is refused so the previous completed record stays stable.
	 */
	private Uni<UUID> startEligibleAttempt(ReactivePersistenceTxContext tx, UUID userId) {
		return attemptDao.findLatestCompletedAt(tx, userId).flatMap(lastCompletedAt -> {
			if (lastCompletedAt.isPresent() && !stabilityWindow.elapsedSince(lastCompletedAt.get(), dateTimeService.getNow())) {
				return Uni.createFrom().failure(new StabilityWindowActiveException(stabilityWindow.endsAfter(lastCompletedAt.get())));
			} else {
				return attemptDao.startInProgress(tx, userId);
			}
		});
	}

	@Override
	public Uni<Void> complete(User user) {
		authorization.requireUserNotService(user);
		return persistenceContextFactory.withTransaction(tx -> completeInTx(tx, user.getId().asUuid()));
	}

	private Uni<Void> completeInTx(ReactivePersistenceTxContext tx, UUID userId) {
		return attemptDao.findInProgressId(tx, userId).flatMap(inProgress -> inProgress
				.map(attemptId -> completeIfEveryQuestionAnswered(tx, attemptId))
				.orElseGet(() -> Uni.createFrom().failure(new NoInProgressAttemptException())));
	}

	private Uni<Void> completeIfEveryQuestionAnswered(ReactivePersistenceTxContext tx, UUID attemptId) {
		return forc(
				questionDao.retrieveAllIds(tx),
				_ -> answerDao.retrieveByAttempt(tx, attemptId),
				(allQuestionIds, answers) -> lockOrRejectAsIncomplete(tx, attemptId, allQuestionIds, answers));
	}

	private Uni<Void> lockOrRejectAsIncomplete(ReactivePersistenceTxContext tx, UUID attemptId, List<QuestionId> allQuestionIds, Map<QuestionId, ScaleOption> answers) {
		var unanswered = allQuestionIds.stream().filter(id -> !answers.containsKey(id)).toList();
		if (unanswered.isEmpty()) {
			return attemptDao.complete(tx, attemptId, dateTimeService.getNow());
		} else {
			return Uni.createFrom().failure(new IncompleteCompassAttemptException(unanswered));
		}
	}
}
