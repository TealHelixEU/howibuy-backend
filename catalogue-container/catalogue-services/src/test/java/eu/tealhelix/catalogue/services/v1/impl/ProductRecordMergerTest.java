package eu.tealhelix.catalogue.services.v1.impl;

import static eu.tealhelix.catalogue.services.v1.impl.RecordOutcome.ADDED;
import static eu.tealhelix.catalogue.services.v1.impl.RecordOutcome.CHANGED;
import static eu.tealhelix.catalogue.services.v1.impl.RecordOutcome.UNCHANGED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Map;

import eu.tealhelix.catalogue.feeds.FeedRecord;
import eu.tealhelix.catalogue.v1.model.ImmutableListing;
import eu.tealhelix.catalogue.v1.model.ImmutableProductChange;
import eu.tealhelix.catalogue.v1.model.ProductChange;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.common.v1.types.RetailerId;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import org.junit.jupiter.api.Test;

class ProductRecordMergerTest {
	private static final RetailerId RETAILER = new GenericRetailerId("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11");
	private static final CatalogueId CATALOGUE = new CatalogueIdImpl("rimi-lt");
	private static final CatalogueId OTHER_CATALOGUE = new CatalogueIdImpl("rimi-lv");
	private static final Currency EUR = Currency.getInstance("EUR");

	private static final FeedRecord RECORD = new FeedRecord(
			1,
			"P-1",
			Map.of("lt", "Pienas", "en", "Milk"),
			Map.of("fat", "2.5%"),
			List.of("dairy"),
			new BigDecimal("1.29"),
			"EUR"
	);

	@Test
	void aProductNeverSeenIsAdded() {
		var merge = ProductRecordMerger.merge(null, RECORD, RETAILER, CATALOGUE);

		assertEquals(ADDED, merge.outcome());
		assertEquals(
				ImmutableProductChange.builder()
						.retailerId(RETAILER)
						.productKey(new ProductKeyImpl("P-1"))
						.changeSequence(0)
						.isRemoved(false)
						.inputHash(ClassificationInputHash.of(RECORD.names(), RECORD.characteristics(), RECORD.tags()))
						.names(RECORD.names())
						.characteristics(RECORD.characteristics())
						.tags(RECORD.tags())
						.addListings(ImmutableListing.builder().catalogueId(CATALOGUE).price(new BigDecimal("1.29")).currency(EUR).isActive(true).build())
						.build(),
				merge.product()
		);
	}

	@Test
	void missingCharacteristicsAndTagsAreNone() {
		var product = ProductRecordMerger.merge(null, new FeedRecord(1, "P-1", Map.of("lt", "Pienas"), null, null, BigDecimal.ONE, "EUR"), RETAILER, CATALOGUE).product();

		assertEquals(Map.of(), product.getCharacteristics());
		assertEquals(List.of(), product.getTags());
	}

	@Test
	void theSameRecordAgainChangesNothing() {
		var stored = stored(RECORD);

		var merge = ProductRecordMerger.merge(stored, RECORD, RETAILER, CATALOGUE);

		assertEquals(UNCHANGED, merge.outcome());
		assertEquals(stored, merge.product());
	}

	@Test
	void theSamePriceInAnotherScaleChangesNothing() {
		var stored = stored(RECORD);

		assertEquals(UNCHANGED, ProductRecordMerger.merge(stored, withPrice("1.2900"), RETAILER, CATALOGUE).outcome());
	}

	@Test
	void theNamesOfTheRecordReplaceThoseOfTheirLanguagesOnly() {
		var stored = stored(RECORD);

		var merge = ProductRecordMerger.merge(stored, withNames(Map.of("lt", "Pienas 2.5%", "ru", "Молоко")), RETAILER, CATALOGUE);

		assertEquals(CHANGED, merge.outcome());
		var names = Map.of("lt", "Pienas 2.5%", "en", "Milk", "ru", "Молоко");
		assertEquals(names, merge.product().getNames());
		assertEquals(ClassificationInputHash.of(names, RECORD.characteristics(), RECORD.tags()), merge.product().getInputHash());
	}

	@Test
	void theCharacteristicsAndTagsOfTheRecordReplaceTheStoredOnes() {
		var stored = stored(RECORD);

		var merge = ProductRecordMerger.merge(stored, new FeedRecord(2, "P-1", RECORD.names(), Map.of("volume", "1l"), List.of("organic"), RECORD.price(), "EUR"), RETAILER, CATALOGUE);

		assertEquals(CHANGED, merge.outcome());
		assertEquals(Map.of("volume", "1l"), merge.product().getCharacteristics());
		assertEquals(List.of("organic"), merge.product().getTags());
		assertNotEquals(stored.getInputHash(), merge.product().getInputHash());
	}

	@Test
	void aPriceChangeKeepsTheHash() {
		var stored = stored(RECORD);

		var merge = ProductRecordMerger.merge(stored, withPrice("1.19"), RETAILER, CATALOGUE);

		assertEquals(CHANGED, merge.outcome());
		assertEquals(new BigDecimal("1.19"), merge.product().getListings().getFirst().getPrice());
		assertEquals(stored.getInputHash(), merge.product().getInputHash());
	}

	@Test
	void aCurrencyChangeIsAChange() {
		var stored = stored(RECORD);

		var merge = ProductRecordMerger.merge(stored, new FeedRecord(2, "P-1", RECORD.names(), RECORD.characteristics(), RECORD.tags(), RECORD.price(), "USD"), RETAILER, CATALOGUE);

		assertEquals(CHANGED, merge.outcome());
		assertEquals(Currency.getInstance("USD"), merge.product().getListings().getFirst().getCurrency());
	}

	@Test
	void anInactiveListingIsReactivatedWithoutChangingTheHash() {
		var stored = ImmutableProductChange.copyOf(stored(RECORD))
				.withListings(ImmutableListing.builder().catalogueId(CATALOGUE).price(RECORD.price()).currency(EUR).isActive(false).build());

		var merge = ProductRecordMerger.merge(stored, RECORD, RETAILER, CATALOGUE);

		assertEquals(CHANGED, merge.outcome());
		assertEquals(true, merge.product().getListings().getFirst().isActive());
		assertEquals(stored.getInputHash(), merge.product().getInputHash());
	}

	@Test
	void aProductOfAnotherCatalogueIsAddedToThisOneAndKeepsTheOtherListing() {
		var stored = ProductRecordMerger.merge(null, RECORD, RETAILER, OTHER_CATALOGUE).product();

		var merge = ProductRecordMerger.merge(stored, RECORD, RETAILER, CATALOGUE);

		assertEquals(ADDED, merge.outcome());
		assertEquals(List.of(OTHER_CATALOGUE, CATALOGUE), merge.product().getListings().stream().map(l -> l.getCatalogueId()).toList());
	}

	@Test
	void aRemovedProductIsRestored() {
		var stored = ImmutableProductChange.copyOf(stored(RECORD)).withIsRemoved(true);

		var merge = ProductRecordMerger.merge(stored, RECORD, RETAILER, CATALOGUE);

		assertEquals(CHANGED, merge.outcome());
		assertFalse(merge.product().isRemoved());
	}

	@Test
	void keepsTheChangeSequenceOfTheStoredProduct() {
		var stored = ImmutableProductChange.copyOf(stored(RECORD)).withChangeSequence(42);

		assertEquals(42, ProductRecordMerger.merge(stored, withPrice("1.19"), RETAILER, CATALOGUE).product().getChangeSequence());
	}

	private static ProductChange stored(FeedRecord record) {
		return ProductRecordMerger.merge(null, record, RETAILER, CATALOGUE).product();
	}

	private static FeedRecord withPrice(String price) {
		return new FeedRecord(2, "P-1", RECORD.names(), RECORD.characteristics(), RECORD.tags(), new BigDecimal(price), "EUR");
	}

	private static FeedRecord withNames(Map<String, String> names) {
		return new FeedRecord(2, "P-1", names, RECORD.characteristics(), RECORD.tags(), RECORD.price(), "EUR");
	}
}
