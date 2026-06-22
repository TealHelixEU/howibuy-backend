package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.NO_SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.AlternativeForProductType.SUGGESTION;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.SUCCESS;

import java.time.Duration;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.services.v1.ProductAssessmentService;
import eu.tealhelix.howibuy.services.v1.authz.HowiBuyAuthorization;
import eu.tealhelix.howibuy.v1.model.ImmutableAlternativeForProduct;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcome;
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

	public ProductAssessmentServiceImpl(HowiBuyAuthorization authorization) {
		this.authorization = authorization;
	}

	@Override
	public Uni<ProductAssessmentOutcome> assessSingleProduct(User user, ProductData productData) {
		authorization.requireUserNotService(user);
		LOG.info("Assess single product as user {}: {}", user.getId().asString(), ProductData.toLogString(productData));
		// TODO Implement for real
		return Uni.createFrom().item(makeDummyProductAssessmentOutcome(productData));
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
