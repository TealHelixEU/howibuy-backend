package eu.tealhelix.sfc.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.sfc.dao.AnswerDao;
import eu.tealhelix.sfc.dao.AttemptDao;
import eu.tealhelix.sfc.dao.CategoryDao;
import eu.tealhelix.sfc.dao.QuestionDao;
import eu.tealhelix.sfc.services.v1.types.AnsweredQuestion;
import eu.tealhelix.sfc.v1.model.Category;
import eu.tealhelix.sfc.v1.model.ImmutableCategory;
import eu.tealhelix.sfc.v1.model.ImmutableQuestion;
import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.CategoryId;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;
import eu.tealhelix.sfc.v1.types.impl.CategoryIdImpl;
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
 * The read service's orchestration with the database mocked: it authorizes the caller, resolves the requested language,
 * overlays the user's current answers onto the localized questions, and computes the frontier ("next question") as the
 * first unanswered question in position order — signalling complete when none remain. The localized joins, ordering and
 * seed data are covered against a real database by {@code CompassStructureTest}; the answer pairing and frontier over a
 * real attempt by {@code CompassNavigationTest}.
 */
@EnableAutoWeld
@AddBeanClasses(TealHelixAuthorizationImpl.class)
@ExtendWith(MockitoExtension.class)
public class CompassReadServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final String USER_ID = "2e788895-0503-4777-a7bd-24e5d61db5b1";
	private static final UUID USER_UUID = UUID.fromString(USER_ID);
	private static final User USER = new UserImpl(new UserIdImpl(USER_ID), null, null, false, false);
	private static final User SERVICE_USER = new UserImpl(new UserIdImpl(USER_ID), null, null, false, true);

	private static final UUID ATTEMPT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

	private static final CategoryId CATEGORY_ID = new CategoryIdImpl("11111111-1111-1111-1111-111111111111");
	private static final List<Category> CATEGORIES = List.of(ImmutableCategory.builder()
			.id(CATEGORY_ID)
			.dimension(SustainabilityDimension.HEALTH)
			.name("Health")
			.description("Health description")
			.build());

	private static final Question Q1 = question("22222222-2222-2222-2222-222222222201", (short) 1);
	private static final Question Q2 = question("22222222-2222-2222-2222-222222222202", (short) 2);
	private static final Question Q3 = question("22222222-2222-2222-2222-222222222203", (short) 3);
	private static final List<Question> QUESTIONS = List.of(Q1, Q2, Q3);

	@Produces
	@Mock
	CategoryDao categoryDao;

	@Produces
	@Mock
	QuestionDao questionDao;

	@Produces
	@Mock
	AttemptDao attemptDao;

	@Produces
	@Mock
	AnswerDao answerDao;

	@Produces
	@ExcludeBean
	SfcLanguages languages = new SfcLanguages(Set.of("en", "el"), "en");

	@Produces
	@RegisterExtension
	MockReactivePersistenceContextFactory mockPersistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Inject
	CompassReadServiceImpl sut;

	@Test
	void findCategoriesReturnsTheDaoResultForTheResolvedLanguage() {
		when(categoryDao.retrieveByLanguage(any(), eq("el"))).thenReturn(Uni.createFrom().item(CATEGORIES));

		var result = sut.findCategories(USER, "el").await().atMost(WAIT);

		assertSame(CATEGORIES, result);
	}

	@Test
	void findCategoriesResolvesAnOmittedLanguageToTheConfiguredDefault() {
		when(categoryDao.retrieveByLanguage(any(), eq("en"))).thenReturn(Uni.createFrom().item(CATEGORIES));

		var result = sut.findCategories(USER, null).await().atMost(WAIT);

		assertSame(CATEGORIES, result);
	}

	@Test
	void findCategoryQuestionsPairsQuestionsWithNoAnswerWhenTheUserHasNoAttempt() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.empty()));
		when(questionDao.retrieveByCategoryAndLanguage(any(), eq(CATEGORY_ID), eq("en"))).thenReturn(Uni.createFrom().item(QUESTIONS));

		var result = sut.findCategoryQuestions(USER, "en", CATEGORY_ID).await().atMost(WAIT);

		assertEquals(List.of(unanswered(Q1), unanswered(Q2), unanswered(Q3)), result);
		verifyNoInteractions(answerDao);
	}

	@Test
	void findCategoryQuestionsPairsEachQuestionWithItsCurrentAnswer() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(ATTEMPT_ID)));
		when(answerDao.retrieveByAttempt(any(), eq(ATTEMPT_ID)))
				.thenReturn(Uni.createFrom().item(Map.of(Q1.getId(), ScaleOption.MODERATELY_IMPORTANT)));
		when(questionDao.retrieveByCategoryAndLanguage(any(), eq(CATEGORY_ID), eq("en"))).thenReturn(Uni.createFrom().item(List.of(Q1, Q2)));

		var result = sut.findCategoryQuestions(USER, "en", CATEGORY_ID).await().atMost(WAIT);

		assertEquals(List.of(answered(Q1, ScaleOption.MODERATELY_IMPORTANT), unanswered(Q2)), result);
	}

	@Test
	void findAllQuestionsPairsEachQuestionWithItsCurrentAnswer() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(ATTEMPT_ID)));
		when(answerDao.retrieveByAttempt(any(), eq(ATTEMPT_ID)))
				.thenReturn(Uni.createFrom().item(Map.of(Q2.getId(), ScaleOption.VERY_IMPORTANT)));
		when(questionDao.retrieveByLanguage(any(), eq("el"))).thenReturn(Uni.createFrom().item(QUESTIONS));

		var result = sut.findAllQuestions(USER, "el").await().atMost(WAIT);

		assertEquals(List.of(unanswered(Q1), answered(Q2, ScaleOption.VERY_IMPORTANT), unanswered(Q3)), result);
	}

	@Test
	void findNextQuestionReturnsTheLowestPositionUnansweredQuestion() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(ATTEMPT_ID)));
		when(answerDao.retrieveByAttempt(any(), eq(ATTEMPT_ID)))
				.thenReturn(Uni.createFrom().item(Map.of(Q1.getId(), ScaleOption.NOT_IMPORTANT)));
		when(questionDao.retrieveByCategoryAndLanguage(any(), eq(CATEGORY_ID), eq("en"))).thenReturn(Uni.createFrom().item(QUESTIONS));

		var result = sut.findNextQuestion(USER, "en", CATEGORY_ID).await().atMost(WAIT);

		assertEquals(Optional.of(Q2), result);
	}

	@Test
	void findNextQuestionRoutesToTheEarliestRemainingAfterAnEarlierAnswerChanges() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(ATTEMPT_ID)));
		when(answerDao.retrieveByAttempt(any(), eq(ATTEMPT_ID)))
				.thenReturn(Uni.createFrom().item(Map.of(Q2.getId(), ScaleOption.NOT_IMPORTANT)));
		when(questionDao.retrieveByCategoryAndLanguage(any(), eq(CATEGORY_ID), eq("en"))).thenReturn(Uni.createFrom().item(QUESTIONS));

		var result = sut.findNextQuestion(USER, "en", CATEGORY_ID).await().atMost(WAIT);

		assertEquals(Optional.of(Q1), result, "the frontier is the earliest unanswered question, not the one after the last answered");
	}

	@Test
	void findNextQuestionSignalsCompleteWhenEveryQuestionIsAnswered() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.of(ATTEMPT_ID)));
		when(answerDao.retrieveByAttempt(any(), eq(ATTEMPT_ID))).thenReturn(Uni.createFrom().item(Map.of(
				Q1.getId(), ScaleOption.NOT_IMPORTANT,
				Q2.getId(), ScaleOption.SLIGHTLY_IMPORTANT,
				Q3.getId(), ScaleOption.VERY_IMPORTANT)));
		when(questionDao.retrieveByCategoryAndLanguage(any(), eq(CATEGORY_ID), eq("en"))).thenReturn(Uni.createFrom().item(QUESTIONS));

		var result = sut.findNextQuestion(USER, "en", CATEGORY_ID).await().atMost(WAIT);

		assertEquals(Optional.empty(), result, "a fully-answered category is complete");
	}

	@Test
	void findNextQuestionWithNoAttemptReturnsTheFirstQuestion() {
		when(attemptDao.findInProgressId(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().item(Optional.empty()));
		when(questionDao.retrieveByCategoryAndLanguage(any(), eq(CATEGORY_ID), eq("en"))).thenReturn(Uni.createFrom().item(QUESTIONS));

		var result = sut.findNextQuestion(USER, "en", CATEGORY_ID).await().atMost(WAIT);

		assertEquals(Optional.of(Q1), result);
		verifyNoInteractions(answerDao);
	}

	@Test
	void findCategoriesRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.findCategories(SERVICE_USER, "en"));
	}

	@Test
	void findCategoryQuestionsRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.findCategoryQuestions(SERVICE_USER, "en", CATEGORY_ID));
	}

	@Test
	void findAllQuestionsRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.findAllQuestions(SERVICE_USER, "en"));
	}

	@Test
	void findNextQuestionRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.findNextQuestion(SERVICE_USER, "en", CATEGORY_ID));
	}

	@Test
	void rejectsAnUnsupportedLanguageWithoutTouchingTheDao() {
		assertThrows(BadInputValueException.class, () -> sut.findCategories(USER, "fr").await().atMost(WAIT));

		verifyNoInteractions(categoryDao);
	}

	private static Question question(String id, short position) {
		return ImmutableQuestion.builder()
				.id(new QuestionIdImpl(id))
				.categoryId(CATEGORY_ID)
				.position(position)
				.text("prompt " + position)
				.build();
	}

	private static AnsweredQuestion unanswered(Question question) {
		return new AnsweredQuestion(question, Optional.empty());
	}

	private static AnsweredQuestion answered(Question question, ScaleOption option) {
		return new AnsweredQuestion(question, Optional.of(option));
	}
}
