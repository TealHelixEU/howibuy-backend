package eu.tealhelix.sfc.services.v1.impl;

import java.util.List;
import java.util.function.BiFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.services.authz.TealHelixAuthorization;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.sfc.dao.CategoryDao;
import eu.tealhelix.sfc.dao.QuestionDao;
import eu.tealhelix.sfc.services.v1.CompassStructureService;
import eu.tealhelix.sfc.v1.model.Category;
import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.CategoryId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class CompassStructureServiceImpl implements CompassStructureService {
	private final TealHelixAuthorization authorization;
	private final CategoryDao categoryDao;
	private final QuestionDao questionDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final SfcLanguages languages;

	@Inject
	public CompassStructureServiceImpl(
			TealHelixAuthorization authorization,
			CategoryDao categoryDao,
			QuestionDao questionDao,
			ReactivePersistenceContextFactory persistenceContextFactory,
			SfcLanguages languages
	) {
		this.authorization = authorization;
		this.categoryDao = categoryDao;
		this.questionDao = questionDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.languages = languages;
	}

	@Override
	public Uni<List<Category>> findCategories(User user, String language) {
		authorization.requireUserNotService(user);
		return localized(language, categoryDao::retrieveByLanguage);
	}

	@Override
	public Uni<List<Question>> findCategoryQuestions(User user, String language, CategoryId categoryId) {
		authorization.requireUserNotService(user);
		return localized(language, (em, lang) -> questionDao.retrieveByCategoryAndLanguage(em, categoryId, lang));
	}

	@Override
	public Uni<List<Question>> findAllQuestions(User user, String language) {
		authorization.requireUserNotService(user);
		return localized(language, questionDao::retrieveByLanguage);
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
