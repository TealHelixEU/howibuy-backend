package eu.tealhelix.sfc.services.v1.impl;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static eu.tealhelix.common.utils.UniComprehensions.forcm;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.sfc.dao.AnswerDao;
import eu.tealhelix.sfc.dao.AttemptDao;
import eu.tealhelix.sfc.dao.CategoryDao;
import eu.tealhelix.sfc.dao.QuestionDao;
import eu.tealhelix.sfc.services.v1.CompassReadService;
import eu.tealhelix.sfc.services.v1.types.AnsweredQuestion;
import eu.tealhelix.sfc.services.v1.types.CategoryOverview;
import eu.tealhelix.sfc.services.v1.types.CompassOverview;
import eu.tealhelix.sfc.services.v1.types.CompletedCompassAnswers;
import eu.tealhelix.sfc.services.v1.types.Progress;
import eu.tealhelix.sfc.v1.model.Category;
import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.AttemptStatus;
import eu.tealhelix.sfc.v1.types.CategoryId;
import eu.tealhelix.sfc.v1.types.QuestionId;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;
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
	private final CompletionEstimator completionEstimator;
	private final ScaleOptionLabels scaleOptionLabels;
	private final StabilityWindow stabilityWindow;
	private final DateTimeService dateTimeService;

	@Inject
	public CompassReadServiceImpl(
			TealHelixAuthorization authorization,
			CategoryDao categoryDao,
			QuestionDao questionDao,
			AttemptDao attemptDao,
			AnswerDao answerDao,
			ReactivePersistenceContextFactory persistenceContextFactory,
			SfcLanguages languages,
			CompletionEstimator completionEstimator,
			ScaleOptionLabels scaleOptionLabels,
			StabilityWindow stabilityWindow,
			DateTimeService dateTimeService
	) {
		this.authorization = authorization;
		this.categoryDao = categoryDao;
		this.questionDao = questionDao;
		this.attemptDao = attemptDao;
		this.answerDao = answerDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.languages = languages;
		this.completionEstimator = completionEstimator;
		this.scaleOptionLabels = scaleOptionLabels;
		this.stabilityWindow = stabilityWindow;
		this.dateTimeService = dateTimeService;
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

	@Override
	public Uni<Optional<AnsweredQuestion>> findPreviousQuestion(User user, String language, CategoryId categoryId) {
		authorization.requireUserNotService(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				Uni.createFrom().item(languages.resolve(language)),
				_ -> currentAnswers(em, user.getId().asUuid()),
				(lang, _) -> questionDao.retrieveByCategoryAndLanguage(em, categoryId, lang),
				(_, answers, questions) -> lastAnswered(questions, answers)
		));
	}

	@Override
	public Uni<CompassOverview> retrieveOverview(User user, String language) {
		authorization.requireUserNotService(user);
		var userId = user.getId().asUuid();
		return localized(language, (em, lang) -> forcm(
				categoryDao.retrieveByLanguage(em, lang),
				_ -> questionDao.retrieveByLanguage(em, lang),
				_ -> attemptState(em, userId),
				(categories, questions, state) -> assemble(lang, categories, questions, state)));
	}

	/**
	 * The user's current attempt reduced to what the overview needs: its status, the questions it counts as answered and
	 * start-a-new-attempt eligibility. The in-progress attempt if there is one (with its actual answers); otherwise the
	 * latest completed attempt (which, being complete, counts every question as answered, and whose stability window
	 * decides eligibility); otherwise no attempt at all.
	 */
	private Uni<AttemptState> attemptState(ReactivePersistenceContext em, UUID userId) {
		return attemptDao.findInProgressId(em, userId).flatMap(inProgress -> inProgress
				.map(attemptId -> answerDao.retrieveByAttempt(em, attemptId).map(answers -> AttemptState.inProgress(answers.keySet())))
				.orElseGet(() -> attemptDao.findLatestCompletedAt(em, userId).map(this::stateFromLatestCompletion)));
	}

	private AttemptState stateFromLatestCompletion(Optional<LocalDateTime> completedAt) {
		return completedAt
				.map(at -> AttemptState.completed(stabilityWindow.endsAfter(at), stabilityWindow.elapsedSince(at, dateTimeService.getNow())))
				.orElseGet(AttemptState::none);
	}

	private CompassOverview assemble(String language, List<Category> categories, List<Question> questions, AttemptState state) {
		var answered = state.coversAll()
				? questions.stream().map(Question::getId).collect(toSet())
				: state.answeredIds();
		var questionsByCategory = questions.stream().collect(groupingBy(Question::getCategoryId));
		var categoryOverviews = categories.stream()
				.map(category -> categoryOverview(category, questionsByCategory.getOrDefault(category.getId(), List.of()), answered))
				.toList();
		return new CompassOverview(
				progressOf(questions, answered),
				completionEstimator.secondsFor(questions.size()),
				categoryOverviews,
				scaleOptionLabels.forLanguage(language),
				state.status(),
				state.eligibleToStartNewAttempt(),
				state.eligibleAt());
	}

	private CategoryOverview categoryOverview(Category category, List<Question> categoryQuestions, Set<QuestionId> answered) {
		return new CategoryOverview(category, progressOf(categoryQuestions, answered), completionEstimator.secondsFor(categoryQuestions.size()));
	}

	private static Progress progressOf(List<Question> questions, Set<QuestionId> answered) {
		var answeredCount = (int) questions.stream().filter(question -> answered.contains(question.getId())).count();
		return Progress.of(answeredCount, questions.size());
	}

	/**
	 * The answers the user's current attempt holds, keyed by question, or an empty map if they have never started one.
	 */
	private Uni<Map<QuestionId, ScaleOption>> currentAnswers(ReactivePersistenceContext em, UUID userId) {
		return currentAttemptId(em, userId)
				.flatMap(attemptId -> attemptId
						.map(id -> answerDao.retrieveByAttempt(em, id))
						.orElseGet(() -> Uni.createFrom().item(Map.of())));
	}

	/**
	 * The attempt the user's answers are read from: the one in progress, or — once they have completed it — their latest
	 * completed one, whose answers are frozen but are still the answers they gave. Empty until they start their first
	 * attempt.
	 */
	private Uni<Optional<UUID>> currentAttemptId(ReactivePersistenceContext em, UUID userId) {
		return attemptDao.findInProgressId(em, userId).flatMap(inProgress -> inProgress.isPresent()
				? Uni.createFrom().item(inProgress)
				: attemptDao.findLatestCompletedId(em, userId));
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
	 * The last question in position order the user has answered, paired with that answer, or empty when they have
	 * answered none of them. The mirror of {@link #frontier}, and defined by the answers alone: a fully answered category
	 * still has a last answered question.
	 */
	private static Optional<AnsweredQuestion> lastAnswered(List<Question> questions, Map<QuestionId, ScaleOption> answers) {
		return questions.reversed().stream()
				.filter(question -> answers.containsKey(question.getId()))
				.findFirst()
				.map(question -> new AnsweredQuestion(question, Optional.of(answers.get(question.getId()))));
	}

	/**
	 * Resolves the requested language (rejecting an unsupported one before touching the DB), then runs the given
	 * read in a non-transactional persistence context.
	 */
	private <T> Uni<T> localized(String language, BiFunction<ReactivePersistenceContext, String, Uni<T>> read) {
		return Uni.createFrom().item(() -> languages.resolve(language))
				.flatMap(lang -> persistenceContextFactory.withoutTransaction(em -> read.apply(em, lang)));
	}

	@Override
	public Uni<Optional<CompletedCompassAnswers>> findLatestCompletedAnswers(User user) {
		authorization.requireUserNotService(user);
		return persistenceContextFactory.withoutTransaction(em ->
				attemptDao.findLatestCompletedId(em, user.getId().asUuid())
						.flatMap(attemptId -> attemptId
								.map(id -> answersByDimension(em, id).map(Optional::of))
								.orElseGet(() -> Uni.createFrom().item(Optional.empty()))));
	}

	private Uni<CompletedCompassAnswers> answersByDimension(ReactivePersistenceContext em, UUID attemptId) {
		return forcm(
				answerDao.retrieveByAttempt(em, attemptId),
				_ -> questionDao.retrieveDimensionsByQuestion(em),
				(answers, dimensions) -> new CompletedCompassAnswers(attemptId, groupByDimension(answers, dimensions)));
	}

	/**
	 * Questions the compass no longer asks may still have answers on an old attempt; they carry no dimension and are
	 * left out rather than gathered under a dimension they no longer belong to.
	 */
	private static Map<SustainabilityDimension, List<ScaleOption>> groupByDimension(
			Map<QuestionId, ScaleOption> answers, Map<QuestionId, SustainabilityDimension> dimensions) {
		var byDimension = new EnumMap<SustainabilityDimension, List<ScaleOption>>(SustainabilityDimension.class);
		for (var answer : answers.entrySet()) {
			var dimension = dimensions.get(answer.getKey());
			if (dimension == null) continue;
			byDimension.computeIfAbsent(dimension, _ -> new ArrayList<>()).add(answer.getValue());
		}
		return byDimension;
	}

	/**
	 * The overview's view of the user's current attempt: its {@link #status} (empty when none has ever been started),
	 * which questions it counts as {@link #answeredIds answered} — or {@link #coversAll every} question, for a completed
	 * attempt — and whether a new attempt may be started now ({@link #eligibleToStartNewAttempt}), with {@link #eligibleAt
	 * when} a re-take becomes possible, set only when the current attempt is a completed one.
	 */
	private record AttemptState(
			Optional<AttemptStatus> status,
			Set<QuestionId> answeredIds,
			boolean coversAll,
			boolean eligibleToStartNewAttempt,
			Optional<LocalDateTime> eligibleAt
	) {
		static AttemptState none() {
			return new AttemptState(Optional.empty(), Set.of(), false, true, Optional.empty());
		}

		static AttemptState inProgress(Set<QuestionId> answeredIds) {
			return new AttemptState(Optional.of(AttemptStatus.IN_PROGRESS), answeredIds, false, false, Optional.empty());
		}

		static AttemptState completed(LocalDateTime windowEnds, boolean eligible) {
			return new AttemptState(Optional.of(AttemptStatus.COMPLETED), Set.of(), true, eligible, Optional.of(windowEnds));
		}
	}
}
