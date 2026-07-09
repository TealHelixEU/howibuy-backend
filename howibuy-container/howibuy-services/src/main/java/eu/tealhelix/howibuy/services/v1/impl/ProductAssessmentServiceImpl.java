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
				l1categories -> extractL1Category(productData, l1categories),
				(l1categories, l1name) -> retrieveSubcategoriesOf(idOfPicked(l1categories, l1name)),
				(_, l1name, l2categories) -> extractSubcategory(productData, l1name, l2categories),
				(_, _, l2categories, l2name) -> retrieveSubcategoriesOf(idOfPicked(l2categories, l2name)),
				(_, l1name, _, l2name, l3categories) -> extractSubsubcategory(productData, l1name, l2name, l3categories),
				(_, _, _, _, l3categories, l3name) -> retrieveProductsInCategory(idOfPicked(l3categories, l3name)),
				(_, l1name, _, l2name, _, l3name, products) -> extractProduct(productData, l1name, l2name, l3name, products)
		).onFailure(FailureToIdentifyException.class).recoverWithUni(e -> failureToIdentifyOutcome(e.getProductData(), e.getDiagnostics()));
	}

	private Uni<List<ArchetypeCategory>> retrieveL1Categories() {
		return persistenceContextFactory.withoutTransaction(archetypeCategoryDao::retrieveL1Categories);
	}

	private Uni<String> extractL1Category(ProductData productData, List<ArchetypeCategory> l1categories) {
		return productAssessmentAiFacade.extractL1Category(productData, categoryNames(l1categories)).flatMap(l1name -> {
			if (isNoMatch(l1name)) {
				return failureToIdentify(productData, diagnostics(null, null, null, null));
			} else {
				return Uni.createFrom().item(l1name);
			}
		});
	}

	private Uni<String> extractSubcategory(ProductData productData, String l1name, List<ArchetypeCategory> l2categories) {
		return productAssessmentAiFacade.extractSubcategory(productData, categoryNames(l2categories)).flatMap(l2name -> {
			if (isNoMatch(l2name)) {
				return failureToIdentify(productData, diagnostics(l1name, null, null, null));
			} else {
				return Uni.createFrom().item(l2name);
			}
		});
	}

	private Uni<String> extractSubsubcategory(ProductData productData, String l1name, String l2name, List<ArchetypeCategory> l3categories) {
		return productAssessmentAiFacade.extractSubcategory(productData, categoryNames(l3categories)).flatMap(l3name -> {
			if (isNoMatch(l3name)) {
				return failureToIdentify(productData, diagnostics(l1name, l2name, null, null));
			} else {
				return Uni.createFrom().item(l3name);
			}
		});
	}

	private Uni<ProductAssessmentOutcome> extractProduct(ProductData productData, String l1name, String l2name, String l3name, List<ArchetypeProduct> products) {
		var productNames = productNames(products);
		return productAssessmentAiFacade.extractArchetypeProduct(productData, productNames).flatMap(productName -> {
			if (isNoMatch(productName)) {
				return failureToIdentifyOutcome(productData, diagnostics(l1name, l2name, l3name, null));
			} else if (!productNames.contains(productName)) {
				return failureToIdentifyOutcome(productData, diagnostics(l1name, l2name, l3name, productName));
			} else {
				return successfulAssessment(productData, diagnostics(l1name, l2name, l3name, productName));
			}
		});
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

	private static <O> Uni<O> failureToIdentify(ProductData productData, ProductAssessmentOutcomeDiagnostics diagnostics) {
		return Uni.createFrom().failure(new FailureToIdentifyException(productData, diagnostics));
	}

	private static Uni<ProductAssessmentOutcome> failureToIdentifyOutcome(ProductData productData, ProductAssessmentOutcomeDiagnostics diagnostics) {
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
