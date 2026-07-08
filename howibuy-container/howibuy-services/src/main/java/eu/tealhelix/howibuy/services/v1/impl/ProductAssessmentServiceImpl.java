package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.NO_SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.SUCCESS;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.dao.ArchetypeCategoryDao;
import eu.tealhelix.howibuy.services.model.ArchetypeCategory;
import eu.tealhelix.howibuy.services.v1.ProductAssessmentService;
import eu.tealhelix.howibuy.services.v1.ai.ProductAssessmentAiFacade;
import eu.tealhelix.howibuy.services.v1.authz.HowiBuyAuthorization;
import eu.tealhelix.howibuy.v1.model.ImmutableAlternativeForProduct;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcomeDiagnostics;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductData;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ProductAssessmentServiceImpl implements ProductAssessmentService {
	private static final Logger LOG = LoggerFactory.getLogger(UserImpersonationServiceImpl.class);

	private final HowiBuyAuthorization authorization;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final ProductAssessmentAiFacade productAssessmentAiFacade;
	private final ArchetypeCategoryDao archetypeCategoryDao;

	public ProductAssessmentServiceImpl(
			HowiBuyAuthorization authorization,
			ReactivePersistenceContextFactory persistenceContextFactory,
			ProductAssessmentAiFacade productAssessmentAiFacade,
			ArchetypeCategoryDao archetypeCategoryDao
	) {
		this.authorization = authorization;
		this.persistenceContextFactory = persistenceContextFactory;
		this.productAssessmentAiFacade = productAssessmentAiFacade;
		this.archetypeCategoryDao = archetypeCategoryDao;
	}

	@Override
	public Uni<ProductAssessmentOutcome> assessSingleProduct(User user, ProductData productData) {
		authorization.requireUserNotService(user);
		LOG.info("Assess single product as user {}: {}", user.getId().asString(), ProductData.toLogString(productData));
		// TODO Implement for real
		return forc(
				retrieveL1Categories(),
				l1categories -> productAssessmentAiFacade.extractL1Category(productData, categoryNames(l1categories)),
				this::retrieveSubcategories,
				(_, l1categoryName, _) -> makeDummyProductAssessmentOutcome(productData, l1categoryName)
		);
	}

	private Uni<List<ArchetypeCategory>> retrieveL1Categories() {
		return persistenceContextFactory.withoutTransaction(archetypeCategoryDao::retrieveL1Categories);
	}

	private Uni<List<ArchetypeCategory>> retrieveSubcategories(List<ArchetypeCategory> categories, String categoryName) {
		var category = findByName(categories, categoryName);
		return retrieveSubcategoriesOf(category.getId());
	}

	private Uni<List<ArchetypeCategory>> retrieveSubcategoriesOf(UUID parentId) {
		return persistenceContextFactory.withoutTransaction(em -> archetypeCategoryDao.retrieveSubcategories(em, parentId));
	}

	private static List<String> categoryNames(List<ArchetypeCategory> categories) {
		return categories.stream().map(ArchetypeCategory::getName).toList();
	}

	private static ArchetypeCategory findByName(List<ArchetypeCategory> categories, String name) {
		return categories.stream()
				.filter(category -> category.getName().equals(name))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"AI picked category which is not among the candidates: '" + name + "'"));
	}

	private Uni<ProductAssessmentOutcome> makeDummyProductAssessmentOutcome(ProductData productData, String l1category) {
		var output = makeDummyProductAssessmentOutcome(productData);
		var outputWithDiagnostics = ((ImmutableProductAssessmentOutcome) output).withDiagnostics(
				ImmutableProductAssessmentOutcomeDiagnostics.builder()
						.product(productData.getName())
						.l1Category(l1category)
						.l2Category("NI")
						.l3Category("NI")
						.build()
		);
		return Uni.createFrom().item(outputWithDiagnostics);
	}

	@Override
	public Uni<List<ProductAssessmentOutcome>> assessMultipleProductsSync(User user, List<ProductData> productsData) {
		authorization.requireUserNotService(user);
		// TODO Implement for real
		return Multi.createFrom().iterable(productsData)
				.map(pd -> {
					LOG.info("Assess (sync) product as user {}: {}", user.getId().asString(), ProductData.toLogString(pd));
					return makeDummyProductAssessmentOutcome(pd);
				})
				.collect().asList();
	}

	@Override
	public Multi<ProductAssessmentOutcome> assessMultipleProductsAsync(User user, List<ProductData> productsData) {
		authorization.requireUserNotService(user);
		// TODO Implement for real
		return Multi.createFrom().iterable(productsData)
				.map(pd -> {
					LOG.info("Assess (sync) product as user {}: {}", user.getId().asString(), ProductData.toLogString(pd));
					return makeDummyProductAssessmentOutcome(pd);
				})
				.onItem()
				.call(item -> Uni.createFrom().nullItem().onItem().delayIt().by(Duration.ofSeconds(1)));
	}

	private ProductAssessmentOutcome makeDummyProductAssessmentOutcome(ProductData productData) {
		var dummyAlternative = ImmutableAlternativeForProduct.builder()
				.type(SUGGESTION)
				.name("The best personal alternative")
				.build();
		return ImmutableProductAssessmentOutcome.builder()
				.productKey(productData.getProductKey())
				.type(SUCCESS)
				.bestPersonalAlternative(dummyAlternative)
				.bestScientificAlternative(ImmutableAlternativeForProduct.builder().type(NO_SUGGESTION).build())
				.bestCombinedAlternative(dummyAlternative)
				.build();
	}
}
