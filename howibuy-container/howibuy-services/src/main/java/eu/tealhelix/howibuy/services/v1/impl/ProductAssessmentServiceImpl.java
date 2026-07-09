package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.common.utils.UniComprehensions.forc;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.NO_SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.SUCCESS;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
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
				l1categories -> pickOrFail(productAssessmentAiFacade.extractL1Category(productData, categoryNames(l1categories)),
						diagnostics(null, null, null, null)),
				(l1categories, l1name) -> retrieveSubcategoriesOf(idOfPicked(l1categories, l1name)),
				(_, l1name, l2categories) -> pickOrFail(productAssessmentAiFacade.extractSubcategory(productData, categoryNames(l2categories)),
						diagnostics(l1name, null, null, null)),
				(_, _, l2categories, l2name) -> retrieveSubcategoriesOf(idOfPicked(l2categories, l2name)),
				(_, l1name, _, l2name, l3categories) -> pickOrFail(productAssessmentAiFacade.extractSubcategory(productData, categoryNames(l3categories)),
						diagnostics(l1name, l2name, null, null)),
				(_, _, _, _, l3categories, l3name) -> retrieveProductsInCategory(idOfPicked(l3categories, l3name)),
				(_, l1name, _, l2name, _, l3name, products) -> extractProduct(productData, l1name, l2name, l3name, products)
		).onFailure(FailureToIdentifyException.class).recoverWithUni(e -> failureToIdentifyOutcome(productData, e.getDiagnostics()));
	}

	private Uni<List<ArchetypeCategory>> retrieveL1Categories() {
		return persistenceContextFactory.withoutTransaction(archetypeCategoryDao::retrieveL1Categories);
	}

	private static Uni<String> pickOrFail(Uni<String> pick, ProductAssessmentOutcomeDiagnostics diagnosticsIfNoMatch) {
		return pick.flatMap(name -> isNoMatch(name)
				? failureToIdentify(diagnosticsIfNoMatch)
				: Uni.createFrom().item(name));
	}

	private Uni<ProductAssessmentOutcome> extractProduct(ProductData productData, String l1name, String l2name, String l3name, List<ArchetypeProduct> products) {
		return productAssessmentAiFacade.extractArchetypeProduct(productData, productNames(products)).flatMap(productName -> {
			if (isNoMatch(productName)) {
				return failureToIdentifyOutcome(productData, diagnostics(l1name, l2name, l3name, null));
			} else {
				requirePicked(products, productName, ArchetypeProduct::getName, "product");
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
		return requirePicked(categories, name, ArchetypeCategory::getName, "category").getId();
	}

	private static <C> C requirePicked(List<C> candidates, String pickedName, Function<C, String> nameOf, String kind) {
		return candidates.stream()
				.filter(candidate -> nameOf.apply(candidate).equals(pickedName))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"AI picked choice which is not among the candidates of kind " + kind + ": '" + pickedName + "'"));
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

	private static <O> Uni<O> failureToIdentify(ProductAssessmentOutcomeDiagnostics diagnostics) {
		return Uni.createFrom().failure(new FailureToIdentifyException(diagnostics));
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
