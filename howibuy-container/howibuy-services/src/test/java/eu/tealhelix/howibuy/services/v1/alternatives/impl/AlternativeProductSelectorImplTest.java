package eu.tealhelix.howibuy.services.v1.alternatives.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Map;

import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.howibuy.services.model.ArchetypeListing;
import eu.tealhelix.howibuy.services.model.ImmutableArchetypeListing;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.RetailerProductIdImpl;
import org.junit.jupiter.api.Test;

class AlternativeProductSelectorImplTest {
	private static final ArchetypeProductId KEFIR = new ArchetypeProductIdImpl("1b0a5a3e-4f7e-4d3f-9a51-0c2b6f1e7a02");

	private final AlternativeProductSelectorImpl sut = new AlternativeProductSelectorImpl();

	@Test
	void selectsTheOnlyListing() {
		var only = listing("11111111-1111-1111-1111-111111111111", "2.49");

		assertEquals(only, sut.select(List.of(only)));
	}

	@Test
	void selectsTheCheapestListing() {
		var cheapest = listing("33333333-3333-3333-3333-333333333333", "1.99");

		assertEquals(cheapest, sut.select(List.of(
				listing("11111111-1111-1111-1111-111111111111", "2.49"),
				cheapest,
				listing("22222222-2222-2222-2222-222222222222", "10"))));
	}

	@Test
	void comparesThePricesByValueWhateverTheirScale() {
		var cheapest = listing("22222222-2222-2222-2222-222222222222", "2.4");

		assertEquals(cheapest, sut.select(List.of(listing("11111111-1111-1111-1111-111111111111", "2.4900"), cheapest)));
	}

	@Test
	void breaksATieOfPricesByTheRetailerProductId() {
		var first = listing("11111111-1111-1111-1111-111111111111", "2.4900");

		assertEquals(first, sut.select(List.of(listing("22222222-2222-2222-2222-222222222222", "2.49"), first)));
	}

	private static ArchetypeListing listing(String retailerProductId, String price) {
		return ImmutableArchetypeListing.builder()
				.id(new RetailerProductIdImpl(retailerProductId))
				.productKey(new ProductKeyImpl("key-" + retailerProductId))
				.archetypeProductId(KEFIR)
				.price(new BigDecimal(price))
				.currency(Currency.getInstance("EUR"))
				.names(Map.of("el", "Κεφίρ"))
				.primaryLanguage("el")
				.build();
	}
}
