package eu.tealhelix.howibuy.services.v1.impl;

import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_OTHER;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.FAILURE_TO_IDENTIFY;
import static eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import eu.tealhelix.common.types.authorization.NotAuthorizedException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.services.v1.authz.impl.HowiBuyAuthorizationImpl;
import eu.tealhelix.howibuy.v1.model.ImmutableProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ImmutableProductData;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductData;
import eu.tealhelix.howibuy.v1.types.ProductAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.impl.ProductKeyImpl;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.jboss.weld.junit5.auto.ExcludeBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@EnableAutoWeld
@AddBeanClasses(HowiBuyAuthorizationImpl.class)
@ExtendWith(MockitoExtension.class)
public class ProductAssessmentServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final User USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, false);
	private static final User SERVICE_USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, true);
	private static final ProductData PRODUCT = product("product-key", "Freshly squeezed orange juice");
	private static final ProductData PRODUCT2 = product("product-key-2", "Some other drink");

	@Produces
	@ExcludeBean
	@Mock
	SingleProductAssessor singleProductAssessor;

	@Inject
	ProductAssessmentServiceImpl sut;

	@Test
	void assessSingleProductReturnsTheAssessorsOutcomeForAValidUser() {
		when(singleProductAssessor.assessOne(PRODUCT)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));

		var outcome = sut.assessSingleProduct(USER, PRODUCT).await().atMost(WAIT);

		assertEquals(PRODUCT.getProductKey(), outcome.getProductKey());
		assertEquals(SUCCESS, outcome.getType());
	}

	@Test
	void assessSingleProductRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.assessSingleProduct(SERVICE_USER, PRODUCT));
	}

	@Test
	void assessMultipleProductsSyncRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.assessMultipleProductsSync(SERVICE_USER, List.of(PRODUCT)));
	}

	@Test
	void assessMultipleProductsAsyncRejectsAServiceUser() {
		assertThrows(NotAuthorizedException.class, () -> sut.assessMultipleProductsAsync(SERVICE_USER, List.of(PRODUCT)));
	}

	@Test
	void syncBatchReportsOneOutcomePerProductInOrder() {
		when(singleProductAssessor.assessOne(PRODUCT)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(PRODUCT2)).thenReturn(Uni.createFrom().item(outcome(PRODUCT2, FAILURE_TO_IDENTIFY)));

		var outcomes = sut.assessMultipleProductsSync(USER, List.of(PRODUCT, PRODUCT2)).await().atMost(WAIT);

		assertEquals(2, outcomes.size());
		assertEquals(PRODUCT.getProductKey(), outcomes.get(0).getProductKey());
		assertEquals(SUCCESS, outcomes.get(0).getType());
		assertEquals(PRODUCT2.getProductKey(), outcomes.get(1).getProductKey());
		assertEquals(FAILURE_TO_IDENTIFY, outcomes.get(1).getType());
	}

	@Test
	void syncBatchIsolatesAnUnexpectedFailureAsFailureOther() {
		when(singleProductAssessor.assessOne(PRODUCT)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(PRODUCT2)).thenReturn(Uni.createFrom().failure(new RuntimeException("DB down")));

		var outcomes = sut.assessMultipleProductsSync(USER, List.of(PRODUCT, PRODUCT2)).await().atMost(WAIT);

		assertEquals(2, outcomes.size());
		assertEquals(SUCCESS, outcomes.get(0).getType());
		assertEquals(PRODUCT2.getProductKey(), outcomes.get(1).getProductKey());
		assertEquals(FAILURE_OTHER, outcomes.get(1).getType());
	}

	@Test
	void asyncBatchStreamsOneOutcomePerProductIsolatingFailures() {
		when(singleProductAssessor.assessOne(PRODUCT)).thenReturn(Uni.createFrom().item(outcome(PRODUCT, SUCCESS)));
		when(singleProductAssessor.assessOne(PRODUCT2)).thenReturn(Uni.createFrom().failure(new RuntimeException("DB down")));

		var outcomes = sut.assessMultipleProductsAsync(USER, List.of(PRODUCT, PRODUCT2))
				.subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE))
				.awaitCompletion(WAIT)
				.getItems();

		assertEquals(2, outcomes.size());
		assertEquals(SUCCESS, outcomes.get(0).getType());
		assertEquals(FAILURE_OTHER, outcomes.get(1).getType());
	}

	private static ProductData product(String key, String name) {
		return ImmutableProductData.builder()
				.productKey(new ProductKeyImpl(key))
				.language(Locale.ENGLISH)
				.name(name)
				.price(new BigDecimal("2.50"))
				.currency(Currency.getInstance("EUR"))
				.build();
	}

	private static ProductAssessmentOutcome outcome(ProductData productData, ProductAssessmentOutcomeType type) {
		return ImmutableProductAssessmentOutcome.builder()
				.productKey(productData.getProductKey())
				.type(type)
				.build();
	}
}
