package eu.tealhelix.sfc.services.v1;

import java.util.List;
import java.util.Optional;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.sfc.services.v1.types.AnsweredQuestion;
import eu.tealhelix.sfc.services.v1.types.CompassOverview;
import eu.tealhelix.sfc.services.v1.types.CompletedCompassAnswers;
import eu.tealhelix.sfc.v1.model.Category;
import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.CategoryId;
import io.smallrye.mutiny.Uni;

/**
 * Reads the compass for a user in a requested language: its fixed structure — categories and questions — overlaid with
 * the answers on the user's current attempt (the one in progress, or, once they have completed it, their latest
 * completed one), plus the "next question" that guides them forward through a category and the "previous question" they
 * step back to. The language argument is the raw request value: {@code null}/blank yields the configured default,
 * and a language outside the configured supported set is rejected (mapped to HTTP 400) rather than served partially
 * translated. Every read requires an authenticated end-user; a service account or unauthenticated caller is rejected.
 */
public interface CompassReadService {
	/**
	 * All categories, localized for {@code language}.
	 */
	Uni<List<Category>> findCategories(User user, String language);

	/**
	 * The questions of one category in position order, localized for {@code language}, each paired with the user's
	 * current answer (or none).
	 */
	Uni<List<AnsweredQuestion>> findCategoryQuestions(User user, String language, CategoryId categoryId);

	/**
	 * Every question across all categories, localized for {@code language} and each paired with the user's current
	 * answer (or none), ordered so questions of the same category are adjacent and categories follow the same order as
	 * {@link #findCategories}.
	 */
	Uni<List<AnsweredQuestion>> findAllQuestions(User user, String language);

	/**
	 * The frontier question of a category — the lowest-position question the user has not yet answered, localized for
	 * {@code language} — or {@link Optional#empty() empty} when every question in the category is answered (the category
	 * is complete). Navigation never advances into another category. A user who has never started an attempt has answered
	 * nothing, so the frontier is the category's first question; a user whose attempt is completed has answered
	 * everything, so there is no frontier.
	 */
	Uni<Optional<Question>> findNextQuestion(User user, String language, CategoryId categoryId);

	/**
	 * The question a user steps back to within a category — the highest-position question they have answered, localized
	 * for {@code language} and paired with that answer so it can be shown as already picked — or
	 * {@link Optional#empty() empty} when they have answered nothing there and so have nothing to step back to. The
	 * mirror of {@link #findNextQuestion}: where next is the first unanswered question, this is the last answered one.
	 * Defined by the answers alone, so a fully answered category still yields its last question. Navigation never
	 * crosses into another category.
	 */
	Uni<Optional<AnsweredQuestion>> findPreviousQuestion(User user, String language, CategoryId categoryId);

	/**
	 * A single overview of where the user stands, localized for {@code language}: every category with overall and
	 * per-category progress and estimated completion time, the five localized scale labels, the current attempt's status
	 * and whether a new attempt may be started now. Progress is measured against the user's current attempt — the one in
	 * progress, or their latest completed one (which, being complete, reads as fully answered), or none.
	 */
	Uni<CompassOverview> retrieveOverview(User user, String language);

	/**
	 * The answers of the user's most recent <em>completed</em> attempt, grouped by dimension, or
	 * {@link Optional#empty() empty} when they have never completed one. An attempt in progress never contributes,
	 * however far along it is: only a completed attempt is a settled statement of what the user cares about.
	 * <p>
	 * Unlike the other reads, this one is not about showing the compass to anyone, so it takes no language.
	 */
	Uni<Optional<CompletedCompassAnswers>> findLatestCompletedAnswers(User user);
}
