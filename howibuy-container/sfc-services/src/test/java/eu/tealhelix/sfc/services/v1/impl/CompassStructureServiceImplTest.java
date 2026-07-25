package eu.tealhelix.sfc.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.common.services.authz.impl.TealHelixAuthorizationImpl;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.sfc.dao.CategoryDao;
import eu.tealhelix.sfc.dao.QuestionDao;
import eu.tealhelix.sfc.v1.model.Category;
import eu.tealhelix.sfc.v1.model.ImmutableCategory;
import eu.tealhelix.sfc.v1.model.ImmutableQuestion;
import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.CategoryId;
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
 * The service's orchestration with the database mocked: it authorizes the caller, resolves the requested language, and
 * delegates to the DAOs — passing their result straight through. DB-detail behaviour (the localized joins, ordering and
 * seed data) is covered separately by {@code CompassStructureTest} against a real database.
 */
@EnableAutoWeld
@AddBeanClasses(TealHelixAuthorizationImpl.class)
@ExtendWith(MockitoExtension.class)
public class CompassStructureServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final User USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, false);
	private static final User SERVICE_USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, true);

	private static final CategoryId CATEGORY_ID = new CategoryIdImpl("11111111-1111-1111-1111-111111111111");
	private static final List<Category> CATEGORIES = List.of(ImmutableCategory.builder()
			.id(CATEGORY_ID)
			.dimension(SustainabilityDimension.HEALTH)
			.name("Health")
			.description("Health description")
			.build());
	private static final List<Question> QUESTIONS = List.of(ImmutableQuestion.builder()
			.id(new QuestionIdImpl("22222222-2222-2222-2222-222222222222"))
			.categoryId(CATEGORY_ID)
			.position((short) 1)
			.text("A prompt")
			.build());

	@Produces
	@Mock
	CategoryDao categoryDao;

	@Produces
	@Mock
	QuestionDao questionDao;

	@Produces
	@ExcludeBean
	SfcLanguages languages = new SfcLanguages(Set.of("en", "el"), "en");

	@Produces
	@RegisterExtension
	MockReactivePersistenceContextFactory mockPersistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Inject
	CompassStructureServiceImpl sut;

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
	void findCategoryQuestionsDelegatesForTheCategoryAndResolvedLanguage() {
		when(questionDao.retrieveByCategoryAndLanguage(any(), eq(CATEGORY_ID), eq("en"))).thenReturn(Uni.createFrom().item(QUESTIONS));

		var result = sut.findCategoryQuestions(USER, "en", CATEGORY_ID).await().atMost(WAIT);

		assertSame(QUESTIONS, result);
	}

	@Test
	void findAllQuestionsDelegatesForTheResolvedLanguage() {
		when(questionDao.retrieveByLanguage(any(), eq("el"))).thenReturn(Uni.createFrom().item(QUESTIONS));

		var result = sut.findAllQuestions(USER, "el").await().atMost(WAIT);

		assertSame(QUESTIONS, result);
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
	void rejectsAnUnsupportedLanguageWithoutTouchingTheDao() {
		assertThrows(BadInputValueException.class, () -> sut.findCategories(USER, "fr").await().atMost(WAIT));

		verifyNoInteractions(categoryDao);
	}
}
