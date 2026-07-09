package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.NO_SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.SUCCESS;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.dao.ArchetypeCategoryDao;
import eu.tealhelix.howibuy.dao.ArchetypeProductDao;
import eu.tealhelix.howibuy.services.model.ArchetypeCategory;
import eu.tealhelix.howibuy.services.model.ArchetypeProduct;
import eu.tealhelix.howibuy.services.v1.ProductAssessmentService;
import eu.tealhelix.howibuy.services.v1.ai.ProductAssessmentAiFacade;
import eu.tealhelix.howibuy.services.v1.authz.HowiBuyAuthorization;
import eu.tealhelix.howibuy.v1.model.ImmutableAlternativeForProduct;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcomeDiagnostics;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcomeDiagnostics;
import eu.tealhelix.howibuy.v1.model.ProductData;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ProductAssessmentServiceImpl implements ProductAssessmentService {
	private static final Logger LOG = LoggerFactory.getLogger(ProductAssessmentServiceImpl.class);
	private static final String NO_MATCH = "NONE";

	private final HowiBuyAuthorization authorization;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final ProductAssessmentAiFacade productAssessmentAiFacade;
	private final ArchetypeCategoryDao archetypeCategoryDao;
	private final ArchetypeProductDao archetypeProductDao;

	@Inject
	public ProductAssessmentServiceImpl(
			HowiBuyAuthorization authorization,
			ReactivePersistenceContextFactory persistenceContextFactory,
			ProductAssessmentAiFacade productAssessmentAiFacade,
			ArchetypeCategoryDao archetypeCategoryDao,
			ArchetypeProductDao archetypeProductDao
	) {
		this.authorization = authorization;
		this.persistenceContextFactory = persistenceContextFactory;
		this.productAssessmentAiFacade = productAssessmentAiFacade;
		this.archetypeCategoryDao = archetypeCategoryDao;
		this.archetypeProductDao = archetypeProductDao;
	}

	@Override
	public Uni<ProductAssessmentOutcome> assessSingleProduct(User user, ProductData productData) {
		authorization.requireUserNotService(user);
		LOG.info("Assess single product as user {}: {}", user.getId().asString(), ProductData.toLogString(productData));
		return forc(
				retrieveL1Categories(),
				l1categories -> productAssessmentAiFacade.extractL1Category(productData, categoryNames(l1categories)),
				(l1categories, l1name) -> descendIntoL2(productData, l1categories, l1name)
		);
	}

	private Uni<ProductAssessmentOutcome> descendIntoL2(ProductData productData, List<ArchetypeCategory> l1categories, String l1name) {
		if (isNoMatch(l1name)) {
			return failureToIdentify(productData, diagnostics(null, null, null, null));
		}
		return forc(
				retrieveSubcategoriesOf(idOfPicked(l1categories, l1name)),
				l2categories -> productAssessmentAiFacade.extractSubcategory(productData, categoryNames(l2categories)),
				(l2categories, l2name) -> descendIntoL3(productData, l1name, l2categories, l2name)
		);
	}

	private Uni<ProductAssessmentOutcome> descendIntoL3(ProductData productData, String l1name, List<ArchetypeCategory> l2categories, String l2name) {
		if (isNoMatch(l2name)) {
			return failureToIdentify(productData, diagnostics(l1name, null, null, null));
		}
		return forc(
				retrieveSubcategoriesOf(idOfPicked(l2categories, l2name)),
				l3categories -> productAssessmentAiFacade.extractSubcategory(productData, categoryNames(l3categories)),
				(l3categories, l3name) -> descendIntoArchetypeProduct(productData, l1name, l2name, l3categories, l3name)
		);
	}

	private Uni<ProductAssessmentOutcome> descendIntoArchetypeProduct(
			ProductData productData, String l1name, String l2name, List<ArchetypeCategory> l3categories, String l3name) {
		if (isNoMatch(l3name)) {
			return failureToIdentify(productData, diagnostics(l1name, l2name, null, null));
		}
		return forc(
				retrieveProductsInCategory(idOfPicked(l3categories, l3name)),
				products -> productAssessmentAiFacade.extractArchetypeProduct(productData, productNames(products)),
				(_, productName) -> completeAssessment(productData, l1name, l2name, l3name, productName)
		);
	}

	private Uni<ProductAssessmentOutcome> completeAssessment(
			ProductData productData, String l1name, String l2name, String l3name, String productName) {
		if (isNoMatch(productName)) {
			return failureToIdentify(productData, diagnostics(l1name, l2name, l3name, null));
		}
		return successfulAssessment(productData, diagnostics(l1name, l2name, l3name, productName));
	}

	private Uni<List<ArchetypeCategory>> retrieveL1Categories() {
		return persistenceContextFactory.withoutTransaction(archetypeCategoryDao::retrieveL1Categories);
	}

	private Uni<List<ArchetypeCategory>> retrieveSubcategoriesOf(UUID parentId) {
		return persistenceContextFactory.withoutTransaction(em -> archetypeCategoryDao.retrieveSubcategories(em, parentId));
	}

	private Uni<List<ArchetypeProduct>> retrieveProductsInCategory(UUID categoryId) {
		return persistenceContextFactory.withoutTransaction(em -> archetypeProductDao.retrieveProductsInCategory(em, categoryId));
	}

	private static List<String> categoryNames(List<ArchetypeCategory> categories) {
		return categories.stream().map(ArchetypeCategory::getName).toList();
	}

	private static List<String> productNames(List<ArchetypeProduct> products) {
		return products.stream().map(ArchetypeProduct::getName).toList();
	}

	private static boolean isNoMatch(String pickedName) {
		return NO_MATCH.equals(pickedName);
	}

	private static UUID idOfPicked(List<ArchetypeCategory> categories, String name) {
		return categories.stream()
				.filter(category -> category.getName().equals(name))
				.findFirst()
				.map(ArchetypeCategory::getId)
				.orElseThrow(() -> new IllegalStateException(
						"AI picked category which is not among the candidates: '" + name + "'"));
	}

	private static ProductAssessmentOutcomeDiagnostics diagnostics(String l1category, String l2category, String l3category, String product) {
		return ImmutableProductAssessmentOutcomeDiagnostics.builder()
				.l1Category(l1category)
				.l2Category(l2category)
				.l3Category(l3category)
				.product(product)
				.build();
	}

	private Uni<ProductAssessmentOutcome> successfulAssessment(ProductData productData, ProductAssessmentOutcomeDiagnostics diagnostics) {
		var output = (ImmutableProductAssessmentOutcome) makeDummyProductAssessmentOutcome(productData);
		return Uni.createFrom().item(output.withDiagnostics(diagnostics));
	}

	private static Uni<ProductAssessmentOutcome> failureToIdentify(ProductData productData, ProductAssessmentOutcomeDiagnostics diagnostics) {
		return Uni.createFrom().item(ImmutableProductAssessmentOutcome.builder()
				.productKey(productData.getProductKey())
				.type(FAILURE_TO_IDENTIFY)
				.diagnostics(diagnostics)
				.build());
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
