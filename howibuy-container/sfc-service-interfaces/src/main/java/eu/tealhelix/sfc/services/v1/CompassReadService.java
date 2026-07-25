package eu.tealhelix.sfc.services.v1;

import java.util.List;
import java.util.Optional;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.sfc.services.v1.types.AnsweredQuestion;
import eu.tealhelix.sfc.v1.model.Category;
import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.CategoryId;
import io.smallrye.mutiny.Uni;

/**
 * Reads the compass for a user in a requested language: its fixed structure — categories and questions — overlaid with
 * the answers the user has given so far on their in-progress attempt, plus the "next question" that guides them forward
 * through a category. The language argument is the raw request value: {@code null}/blank yields the configured default,
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
	 * is complete). Navigation never advances into another category. A user with no in-progress attempt has answered
	 * nothing, so the frontier is the category's first question.
	 */
	Uni<Optional<Question>> findNextQuestion(User user, String language, CategoryId categoryId);
}
