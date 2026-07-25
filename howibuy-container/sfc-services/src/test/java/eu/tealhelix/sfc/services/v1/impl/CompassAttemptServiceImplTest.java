package eu.tealhelix.sfc.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthenticatedException;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.sfc.dao.AnswerDao;
import eu.tealhelix.sfc.dao.AttemptDao;
import eu.tealhelix.sfc.v1.types.QuestionId;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import eu.tealhelix.sfc.v1.types.impl.QuestionIdImpl;
import io.smallrye.mutiny.Uni;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The service's orchestration with the database mocked: it authorizes the caller, then either starts a fresh attempt
 * (first answer) or reuses the in-progress one, and upserts the answer against that attempt. The actual persistence —
 * the immediate save, the overwrite, and the one-in-progress and 1–5 constraints — is covered against a real database
 * by {@code CompassAnswerTest}.
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

	private static final QuestionId QUESTION_ID = new QuestionIdImpl("22222222-2222-2222-2222-222222222222");
	private static final UUID EXISTING_ATTEMPT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID NEW_ATTEMPT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

	@Produces
	@Mock
	AttemptDao attemptDao;

	@Produces
	@Mock
	AnswerDao answerDao;

	@Produces
	@RegisterExtension
	MockReactivePersistenceContextFactory mockPersistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Inject
	CompassAttemptServiceImpl sut;

	@Test
	void firstAnswerStartsAnAttemptThenSavesTheAnswer() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.empty()));
		when(attemptDao.startInProgress(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(NEW_ATTEMPT_ID));
		when(answerDao.upsert(any(), eq(NEW_ATTEMPT_ID), eq(QUESTION_ID), eq(ScaleOption.VERY_IMPORTANT)))
				.thenReturn(Uni.createFrom().voidItem());

		sut.answer(USER, QUESTION_ID, ScaleOption.VERY_IMPORTANT).await().atMost(WAIT);

		verify(attemptDao).startInProgress(any(), eq(USER_UUID));
		verify(answerDao).upsert(any(), eq(NEW_ATTEMPT_ID), eq(QUESTION_ID), eq(ScaleOption.VERY_IMPORTANT));
	}

	@Test
	void answerOnAnExistingAttemptReusesItWithoutStartingANewOne() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(EXISTING_ATTEMPT_ID)));
		when(answerDao.upsert(any(), eq(EXISTING_ATTEMPT_ID), eq(QUESTION_ID), eq(ScaleOption.MODERATELY_IMPORTANT)))
				.thenReturn(Uni.createFrom().voidItem());

		sut.answer(USER, QUESTION_ID, ScaleOption.MODERATELY_IMPORTANT).await().atMost(WAIT);

		verify(attemptDao, never()).startInProgress(any(), any());
		verify(answerDao).upsert(any(), eq(EXISTING_ATTEMPT_ID), eq(QUESTION_ID), eq(ScaleOption.MODERATELY_IMPORTANT));
	}

	@Test
	void rejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.answer(SERVICE_USER, QUESTION_ID, ScaleOption.VERY_IMPORTANT));
		verifyNoInteractions(attemptDao, answerDao);
	}

	@Test
	void rejectsAnUnauthenticatedUser() {
		assertThrows(NotAuthenticatedException.class, () -> sut.answer(UNAUTHENTICATED, QUESTION_ID, ScaleOption.VERY_IMPORTANT));
		verifyNoInteractions(attemptDao, answerDao);
	}

	@Test
	void rejectsAMissingOption() {
		assertThrows(RequiredInputMissingException.class, () -> sut.answer(USER, QUESTION_ID, null));
		verifyNoInteractions(attemptDao, answerDao);
	}
}
