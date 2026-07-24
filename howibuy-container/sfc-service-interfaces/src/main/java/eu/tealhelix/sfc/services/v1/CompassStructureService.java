package eu.tealhelix.sfc.services.v1;

import java.util.List;
import java.util.UUID;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.sfc.v1.model.Category;
import eu.tealhelix.sfc.v1.model.Question;
import io.smallrye.mutiny.Uni;

/**
 * Reads the compass's fixed structure — its categories and questions — with content resolved for a requested language.
 * The language argument is the raw request value: {@code null}/blank yields the configured default, and a language
 * outside the configured supported set is rejected (mapped to HTTP 400) rather than served partially translated.
 * Every read requires an authenticated end-user; a service account or unauthenticated caller is rejected.
 */
public interface CompassStructureService {
	/**
	 * All categories, localized for {@code language}.
	 */
	Uni<List<Category>> findCategories(User user, String language);

	/**
	 * The questions of one category in position order, localized for {@code language}.
	 */
	Uni<List<Question>> findCategoryQuestions(User user, String language, UUID categoryId);

	/**
	 * Every question across all categories, localized for {@code language}, ordered so questions of the same category
	 * are adjacent and categories follow the same order as {@link #findCategories}.
	 */
	Uni<List<Question>> findAllQuestions(User user, String language);
}
