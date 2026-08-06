package eu.tealhelix.sfc.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthenticatedException;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.sfc.dao.AnswerDao;
import eu.tealhelix.sfc.dao.AttemptDao;
import eu.tealhelix.sfc.dao.QuestionDao;
import eu.tealhelix.sfc.services.v1.types.AttemptAlreadyInProgressException;
import eu.tealhelix.sfc.services.v1.types.IncompleteCompassAttemptException;
import eu.tealhelix.sfc.services.v1.types.NoInProgressAttemptException;
import eu.tealhelix.sfc.services.v1.types.StabilityWindowActiveException;
import eu.tealhelix.sfc.v1.types.QuestionId;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import eu.tealhelix.sfc.v1.types.impl.QuestionIdImpl;
import io.smallrye.mutiny.Uni;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.jboss.weld.junit5.auto.ExcludeBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The service's orchestration with the database mocked: it authorizes the caller, then either starts a fresh attempt
 * (first answer) or reuses the in-progress one and upserts the answer; when a completed attempt is still within its
 * stability window a new attempt is refused, and once elapsed a fresh attempt starts — whether the user asks for one
 * outright or simply answers; completion validates every question is answered before locking, else rejects with the
 * unanswered ids. The actual persistence — the immediate
 * save, the overwrite, the one-in-progress and 1–5 constraints, the freeze and the window over real time — is covered
 * against a real database by {@code CompassAnswerTest} and {@code CompassCompletionTest}; the window comparison in
 * isolation by {@code StabilityWindowTest}.
 */
@EnableAutoWeld
@AddBeanClasses(TealHelixAuthorizationImpl.class)
@ExtendWith(MockitoExtension.class)
public class CompassAttemptServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final String USER_ID = "2e788895-0503-4777-a7bd-24e5d61db5b1";
	private static final UUID USER_UUID = UUID.fromString(USER_ID);
	private static final User USER = new UserImpl(new UserIdImpl(USER_ID), null, null, false, false);
	private static final User SERVICE_USER = new UserImpl(new UserIdImpl(USER_ID), null, null, false, true);
	private static final User UNAUTHENTICATED = new UserImpl(null, null, null, false, false);

	private static final QuestionId QUESTION_1 = new QuestionIdImpl("22222222-2222-2222-2222-222222222201");
	private static final QuestionId QUESTION_2 = new QuestionIdImpl("22222222-2222-2222-2222-222222222202");
	private static final UUID EXISTING_ATTEMPT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID NEW_ATTEMPT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

	private static final Duration STABILITY_WINDOW = Duration.ofDays(30);
	private static final LocalDateTime PRIOR_COMPLETION = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
	private static final LocalDateTime WITHIN_WINDOW = PRIOR_COMPLETION.plusDays(10);
	private static final LocalDateTime AFTER_WINDOW = PRIOR_COMPLETION.plusDays(31);
	private static final LocalDateTime COMPLETION_TIME = LocalDateTime.of(2026, 5, 5, 8, 0, 0);

	@Produces
	@Mock
	AttemptDao attemptDao;

	@Produces
	@Mock
	AnswerDao answerDao;

	@Produces
	@Mock
	QuestionDao questionDao;

	@Produces
	@Mock
	DateTimeService dateTimeService;

	@Produces
	@ExcludeBean
	StabilityWindow stabilityWindow = new StabilityWindow(STABILITY_WINDOW);

	@Produces
	@RegisterExtension
	MockReactivePersistenceContextFactory mockPersistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Inject
	CompassAttemptServiceImpl sut;

	@Test
	void firstEverAnswerStartsAnAttemptThenSavesTheAnswer() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.empty()));
		when(attemptDao.findLatestCompletedAt(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.empty()));
		when(attemptDao.startInProgress(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(NEW_ATTEMPT_ID));
		when(answerDao.upsert(any(), eq(NEW_ATTEMPT_ID), eq(QUESTION_1), eq(ScaleOption.VERY_IMPORTANT)))
				.thenReturn(Uni.createFrom().voidItem());

		sut.answer(USER, QUESTION_1, ScaleOption.VERY_IMPORTANT).await().atMost(WAIT);

		verify(attemptDao).startInProgress(any(), eq(USER_UUID));
		verify(answerDao).upsert(any(), eq(NEW_ATTEMPT_ID), eq(QUESTION_1), eq(ScaleOption.VERY_IMPORTANT));
	}

	@Test
	void answerOnAnExistingAttemptReusesItWithoutCheckingEligibility() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(EXISTING_ATTEMPT_ID)));
		when(answerDao.upsert(any(), eq(EXISTING_ATTEMPT_ID), eq(QUESTION_1), eq(ScaleOption.MODERATELY_IMPORTANT)))
				.thenReturn(Uni.createFrom().voidItem());

		sut.answer(USER, QUESTION_1, ScaleOption.MODERATELY_IMPORTANT).await().atMost(WAIT);

		verify(attemptDao, never()).startInProgress(any(), any());
		verify(attemptDao, never()).findLatestCompletedAt(any(), any());
		verify(answerDao).upsert(any(), eq(EXISTING_ATTEMPT_ID), eq(QUESTION_1), eq(ScaleOption.MODERATELY_IMPORTANT));
	}

	@Test
	void answeringWithinAPriorAttemptsStabilityWindowIsRefused() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.empty()));
		when(attemptDao.findLatestCompletedAt(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(PRIOR_COMPLETION)));
		when(dateTimeService.getNow()).thenReturn(WITHIN_WINDOW);

		var ex = assertThrows(StabilityWindowActiveException.class, () -> sut.answer(USER, QUESTION_1, ScaleOption.VERY_IMPORTANT).await().atMost(WAIT));

		assertEquals(PRIOR_COMPLETION.plus(STABILITY_WINDOW), ex.getEligibleAt(), "the caller is told when a new attempt becomes possible");
		verify(attemptDao, never()).startInProgress(any(), any());
		verifyNoInteractions(answerDao);
	}

	@Test
	void answeringAfterTheStabilityWindowStartsAFreshAttempt() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.empty()));
		when(attemptDao.findLatestCompletedAt(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(PRIOR_COMPLETION)));
		when(dateTimeService.getNow()).thenReturn(AFTER_WINDOW);
		when(attemptDao.startInProgress(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(NEW_ATTEMPT_ID));
		when(answerDao.upsert(any(), eq(NEW_ATTEMPT_ID), eq(QUESTION_1), eq(ScaleOption.VERY_IMPORTANT)))
				.thenReturn(Uni.createFrom().voidItem());

		sut.answer(USER, QUESTION_1, ScaleOption.VERY_IMPORTANT).await().atMost(WAIT);

		verify(attemptDao).startInProgress(any(), eq(USER_UUID));
		verify(answerDao).upsert(any(), eq(NEW_ATTEMPT_ID), eq(QUESTION_1), eq(ScaleOption.VERY_IMPORTANT));
	}

	@Test
	void startNewAttemptStartsABlankAttemptOnceThePriorWindowHasElapsed() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.empty()));
		when(attemptDao.findLatestCompletedAt(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(PRIOR_COMPLETION)));
		when(dateTimeService.getNow()).thenReturn(AFTER_WINDOW);
		when(attemptDao.startInProgress(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(NEW_ATTEMPT_ID));

		sut.startNewAttempt(USER).await().atMost(WAIT);

		verify(attemptDao).startInProgress(any(), eq(USER_UUID));
		verifyNoInteractions(answerDao);
	}

	@Test
	void startNewAttemptIsRefusedWhileOneIsAlreadyInProgress() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(EXISTING_ATTEMPT_ID)));

		assertThrows(AttemptAlreadyInProgressException.class, () -> sut.startNewAttempt(USER).await().atMost(WAIT));

		verify(attemptDao, never()).startInProgress(any(), any());
		verify(attemptDao, never()).findLatestCompletedAt(any(), any());
	}

	@Test
	void startNewAttemptWithinAPriorAttemptsStabilityWindowIsRefused() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.empty()));
		when(attemptDao.findLatestCompletedAt(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(PRIOR_COMPLETION)));
		when(dateTimeService.getNow()).thenReturn(WITHIN_WINDOW);

		var ex = assertThrows(StabilityWindowActiveException.class, () -> sut.startNewAttempt(USER).await().atMost(WAIT));

		assertEquals(PRIOR_COMPLETION.plus(STABILITY_WINDOW), ex.getEligibleAt(), "the caller is told when a new attempt becomes possible");
		verify(attemptDao, never()).startInProgress(any(), any());
	}

	@Test
	void startNewAttemptRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.startNewAttempt(SERVICE_USER));
		verifyNoInteractions(attemptDao, answerDao, questionDao);
	}

	@Test
	void completeLocksTheAttemptWhenEveryQuestionIsAnswered() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(EXISTING_ATTEMPT_ID)));
		when(questionDao.retrieveAllIds(any())).thenReturn(Uni.createFrom().item(List.of(QUESTION_1, QUESTION_2)));
		when(answerDao.retrieveByAttempt(any(), eq(EXISTING_ATTEMPT_ID))).thenReturn(Uni.createFrom().item(
				Map.of(QUESTION_1, ScaleOption.VERY_IMPORTANT, QUESTION_2, ScaleOption.NOT_IMPORTANT)));
		when(dateTimeService.getNow()).thenReturn(COMPLETION_TIME);
		when(attemptDao.complete(any(), eq(EXISTING_ATTEMPT_ID), eq(COMPLETION_TIME))).thenReturn(Uni.createFrom().voidItem());

		sut.complete(USER).await().atMost(WAIT);

		verify(attemptDao).complete(any(), eq(EXISTING_ATTEMPT_ID), eq(COMPLETION_TIME));
	}

	@Test
	void completeRejectsWithTheUnansweredQuestionsWhenSomeRemain() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(EXISTING_ATTEMPT_ID)));
		when(questionDao.retrieveAllIds(any())).thenReturn(Uni.createFrom().item(List.of(QUESTION_1, QUESTION_2)));
		when(answerDao.retrieveByAttempt(any(), eq(EXISTING_ATTEMPT_ID)))
				.thenReturn(Uni.createFrom().item(Map.of(QUESTION_1, ScaleOption.VERY_IMPORTANT)));

		var ex = assertThrows(IncompleteCompassAttemptException.class, () -> sut.complete(USER).await().atMost(WAIT));

		assertEquals(List.of(QUESTION_2), ex.getUnansweredQuestionIds(), "exactly the still-unanswered questions");
		verify(attemptDao, never()).complete(any(), any(), any());
	}

	@Test
	void completeIsRejectedWhenThereIsNoAttemptInProgress() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.empty()));

		assertThrows(NoInProgressAttemptException.class, () -> sut.complete(USER).await().atMost(WAIT));

		verifyNoInteractions(questionDao, answerDao);
		verify(attemptDao, never()).complete(any(), any(), any());
	}

	@Test
	void rejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.answer(SERVICE_USER, QUESTION_1, ScaleOption.VERY_IMPORTANT));
		verifyNoInteractions(attemptDao, answerDao, questionDao);
	}

	@Test
	void rejectsAnUnauthenticatedUser() {
		assertThrows(NotAuthenticatedException.class, () -> sut.answer(UNAUTHENTICATED, QUESTION_1, ScaleOption.VERY_IMPORTANT));
		verifyNoInteractions(attemptDao, answerDao, questionDao);
	}

	@Test
	void rejectsAMissingOption() {
		assertThrows(RequiredInputMissingException.class, () -> sut.answer(USER, QUESTION_1, null));
		verifyNoInteractions(attemptDao, answerDao, questionDao);
	}

	@Test
	void completeRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.complete(SERVICE_USER));
		verifyNoInteractions(attemptDao, answerDao, questionDao);
	}
}
