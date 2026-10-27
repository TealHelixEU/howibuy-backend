package eu.tealhelix.howibuy.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.howibuy.dao.ClassificationDao;
import eu.tealhelix.howibuy.services.model.ArchetypeListing;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeListing;
import eu.tealhelix.howibuy.services.v1.alternatives.impl.AlternativeProductSelectorImpl;
import eu.tealhelix.howibuy.v1.model.ListedProduct;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.RetailerProductIdImpl;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Which product of the Catalogue stands for each recommended archetype, and how the user sees it, with the database
 * mocked. Which Listings the database finds is covered by {@code ClassificationDaoImplTest}.
 */
@ExtendWith(MockitoExtension.class)
class CatalogueAssortmentServiceTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final CatalogueId CATALOGUE = new CatalogueIdImpl("lime-fresh-gr");
	private static final ArchetypeProductId YOGHURT = new ArchetypeProductIdImpl("1b0a5a3e-4f7e-4d3f-9a51-0c2b6f1e7a01");
	private static final ArchetypeProductId KEFIR = new ArchetypeProductIdImpl("1b0a5a3e-4f7e-4d3f-9a51-0c2b6f1e7a02");

	@Mock
	ClassificationDao classificationDao;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	private CatalogueAssortmentService sut;

	@BeforeEach
	void setUp() {
		sut = new CatalogueAssortmentService(persistenceContextFactory, classificationDao, new AlternativeProductSelectorImpl());
	}

	@Test
	void eachArchetypeIsRepresentedByTheProductTheSelectorPicksAmongItsOwnListings() {
		givenListings("el",
				listing(YOGHURT, "11111111-1111-1111-1111-111111111111", "2.49", Map.of("el", "Γιαούρτι")),
				listing(KEFIR, "22222222-2222-2222-2222-222222222222", "3.10", Map.of("el", "Κεφίρ")),
				listing(YOGHURT, "33333333-3333-3333-3333-333333333333", "1.99", Map.of("el", "Γιαούρτι οικονομικό")),
				listing(KEFIR, "44444444-4444-4444-4444-444444444444", "2.80", Map.of("el", "Κεφίρ οικονομικό")));

		var products = listedProducts("el");

		assertEquals(Set.of(YOGHURT, KEFIR), products.keySet());
		assertEquals("key-33333333-3333-3333-3333-333333333333", products.get(YOGHURT).getProductKey().asString(), "the cheaper yoghurt");
		assertEquals("key-44444444-4444-4444-4444-444444444444", products.get(KEFIR).getProductKey().asString(), "the cheaper kefir");
	}

	@Test
	void theProductIsNamedInTheLanguageAskedForWhenItHasANameInIt() {
		givenListings("en", listing(KEFIR, "11111111-1111-1111-1111-111111111111", "2.49", Map.of("el", "Κεφίρ", "en", "Kefir")));

		var product = listedProducts("en").get(KEFIR);

		assertEquals("Kefir", product.getName());
		assertEquals("en", product.getLanguage());
	}

	@Test
	void theProductIsNamedInTheCataloguesPrimaryLanguageWhenItHasNoNameInTheLanguageAskedFor() {
		givenListings("en", listing(KEFIR, "11111111-1111-1111-1111-111111111111", "2.49", Map.of("el", "Κεφίρ")));

		var product = listedProducts("en").get(KEFIR);

		assertEquals("Κεφίρ", product.getName());
		assertEquals("el", product.getLanguage(), "the language the name is actually in");
	}

	@Test
	void thePriceAndCurrencyAreThoseOfTheListing() {
		givenListings("el", listing(KEFIR, "11111111-1111-1111-1111-111111111111", "2.4900", Map.of("el", "Κεφίρ")));

		var product = listedProducts("el").get(KEFIR);

		assertEquals("2.49", product.getPrice().toPlainString(), "without trailing zeros");
		assertEquals(Currency.getInstance("EUR"), product.getCurrency());
	}

	@Test
	void aRoundPriceIsAWholeNumberRatherThanAPowerOfTen() {
		givenListings("el", listing(KEFIR, "11111111-1111-1111-1111-111111111111", "10.0000", Map.of("el", "Κεφίρ")));

		var price = listedProducts("el").get(KEFIR).getPrice();

		assertEquals("10", price.toString(), "not 1E+1, which is how a number with its trailing zeros stripped prints");
	}

	private void givenListings(String language, ArchetypeListing... listings) {
		when(classificationDao.findListingsOfArchetypes(any(), eq(CATALOGUE), eq(Set.of(YOGHURT, KEFIR)), eq(language)))
				.thenReturn(Uni.createFrom().item(List.of(listings)));
	}

	private Map<ArchetypeProductId, ListedProduct> listedProducts(String language) {
		return sut.listedProducts(CATALOGUE, Set.of(YOGHURT, KEFIR), language).await().atMost(WAIT);
	}

	private static ArchetypeListing listing(ArchetypeProductId archetypeId, String retailerProductId, String price, Map<String, String> names) {
		return ImmutableArchetypeListing.builder()
				.id(new RetailerProductIdImpl(retailerProductId))
				.productKey(new ProductKeyImpl("key-" + retailerProductId))
				.archetypeProductId(archetypeId)
				.price(new BigDecimal(price))
				.currency(Currency.getInstance("EUR"))
				.names(names)
				.primaryLanguage("el")
				.build();
	}
}
