package eu.tealhelix.sfc.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forcm;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.sfc.dao.AnswerDao;
import eu.tealhelix.sfc.dao.AttemptDao;
import eu.tealhelix.sfc.dao.CategoryDao;
import eu.tealhelix.sfc.dao.QuestionDao;
import eu.tealhelix.sfc.services.v1.CompassReadService;
import eu.tealhelix.sfc.services.v1.types.AnsweredQuestion;
import eu.tealhelix.sfc.v1.model.Category;
import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.CategoryId;
import eu.tealhelix.sfc.v1.types.QuestionId;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class CompassReadServiceImpl implements CompassReadService {
	private final TealHelixAuthorization authorization;
	private final CategoryDao categoryDao;
	private final QuestionDao questionDao;
	private final AttemptDao attemptDao;
	private final AnswerDao answerDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final SfcLanguages languages;

	@Inject
	public CompassReadServiceImpl(
			TealHelixAuthorization authorization,
			CategoryDao categoryDao,
			QuestionDao questionDao,
			AttemptDao attemptDao,
			AnswerDao answerDao,
			ReactivePersistenceContextFactory persistenceContextFactory,
			SfcLanguages languages
	) {
		this.authorization = authorization;
		this.categoryDao = categoryDao;
		this.questionDao = questionDao;
		this.attemptDao = attemptDao;
		this.answerDao = answerDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.languages = languages;
	}

	@Override
	public Uni<List<Category>> findCategories(User user, String language) {
		authorization.requireUserNotService(user);
		return Uni.createFrom().item(() -> languages.resolve(language))
				.flatMap(lang -> persistenceContextFactory.withoutTransaction(em -> categoryDao.retrieveByLanguage(em, lang)));
	}

	@Override
	public Uni<List<AnsweredQuestion>> findCategoryQuestions(User user, String language, CategoryId categoryId) {
		authorization.requireUserNotService(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				Uni.createFrom().item(languages.resolve(language)),
				_ -> currentAnswers(em, user.getId().asUuid()),
				(lang, _) -> questionDao.retrieveByCategoryAndLanguage(em, categoryId, lang),
				(_, answers, questions) -> pairWithAnswers(questions, answers)
		));
	}

	@Override
	public Uni<List<AnsweredQuestion>> findAllQuestions(User user, String language) {
		authorization.requireUserNotService(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				Uni.createFrom().item(languages.resolve(language)),
				_ -> currentAnswers(em, user.getId().asUuid()),
				(lang, _) -> questionDao.retrieveByLanguage(em, lang),
				(_, answers, questions) -> pairWithAnswers(questions, answers)
		));
	}

	@Override
	public Uni<Optional<Question>> findNextQuestion(User user, String language, CategoryId categoryId) {
		authorization.requireUserNotService(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				Uni.createFrom().item(languages.resolve(language)),
				_ -> currentAnswers(em, user.getId().asUuid()),
				(lang, _) -> questionDao.retrieveByCategoryAndLanguage(em, categoryId, lang),
				(_, answers, questions) -> frontier(questions, answers)
		));
	}

	/**
	 * The answers on the user's in-progress attempt, keyed by question, or an empty map if they have no attempt yet.
	 */
	private Uni<Map<QuestionId, ScaleOption>> currentAnswers(ReactivePersistenceContext em, UUID userId) {
		return attemptDao.findInProgressId(em, userId)
				.flatMap(attemptId -> attemptId
						.map(id -> answerDao.retrieveByAttempt(em, id))
						.orElseGet(() -> Uni.createFrom().item(Map.of())));
	}

	private static List<AnsweredQuestion> pairWithAnswers(List<Question> questions, Map<QuestionId, ScaleOption> answers) {
		return questions.stream()
				.map(question -> new AnsweredQuestion(question, Optional.ofNullable(answers.get(question.getId()))))
				.toList();
	}

	/**
	 * The first question in position order the user has not answered — the frontier — or empty when all are answered.
	 * Independent of which questions were answered when: changing an earlier answer still leaves the earliest remaining
	 * unanswered question as the frontier.
	 */
	private static Optional<Question> frontier(List<Question> questions, Map<QuestionId, ScaleOption> answers) {
		return questions.stream()
				.filter(question -> !answers.containsKey(question.getId()))
				.findFirst();
	}

	/**
	 * Resolves the requested language (rejecting an unsupported one before touching the DB), then runs the given
	 * read in a non-transactional persistence context.
	 */
	private <T> Uni<T> localized(String language, BiFunction<ReactivePersistenceContext, String, Uni<T>> read) {
		return Uni.createFrom().item(() -> languages.resolve(language))
				.flatMap(lang -> persistenceContextFactory.withoutTransaction(em -> read.apply(em, lang)));
	}
}
