package eu.tealhelix.catalogue.feeds.rimi;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import eu.tealhelix.catalogue.feeds.FeedRecord;
import eu.tealhelix.catalogue.feeds.RejectedRecord;
import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableCatalogue;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import org.junit.jupiter.api.Test;

class RimiFeedReaderTest {
	private static final Catalogue CATALOGUE = ImmutableCatalogue.builder()
			.id(new CatalogueIdImpl("rimi-lt"))
			.retailerId(new GenericRetailerId("3c9a4a52-55c4-4f0e-9a3b-6f7d2c1e8b40"))
			.country("LT")
			.primaryLanguage("lt")
			.acceptedLanguages(Set.of("lt", "en"))
			.adapter(RimiFeedReader.NAME)
			.isActive(true)
			.build();
	private static final List<String> HEADER = List.of(
			"item", "title", "link", "category", "available", "price", "brand",
			"title_LV", "category_LV", "available_LV", "price_LV", "brand_LV",
			"title_LT", "category_LT", "available_LT", "price_LT", "description_LT", "brand_LT",
			"c_countryOfOrigin", "c_organic", "c_glutenFree", "c_sugarFree", "c_lactoseFree", "c_dairyFree",
			"c_suitableForVegetarians", "c_suitableForVegans", "c_healthy", "c_novelty"
	);
	/**
	 * A food row of the Catalogue, which a test changes in the values it is about.
	 */
	private static final Map<String, String> MILK = Map.ofEntries(
			Map.entry("item", "1000001"),
			Map.entry("title", "Milk ROKIŠKIO 2.5%, 1 l"),
			Map.entry("link", "https://storefront-uat-lt.onrimi.net/e-parduotuve/en/products/dairy-products-eggs-cheese/milk/p/1000001"),
			Map.entry("category", "products > dairy-products-eggs-cheese > milk"),
			Map.entry("available", "true"),
			Map.entry("price", "1.29"),
			Map.entry("brand", "Rokiškio"),
			Map.entry("title_LT", "Pienas ROKIŠKIO 2,5 %, 1 l"),
			Map.entry("category_LT", "produktai > pieno-produktai-kiausiniai-ir-suris > pienas"),
			Map.entry("available_LT", "true"),
			Map.entry("price_LT", "1.29"),
			Map.entry("description_LT", "Pasterizuotas pienas"),
			Map.entry("brand_LT", "Rokiškio"),
			Map.entry("c_countryOfOrigin", "LT"),
			Map.entry("c_organic", "false"),
			Map.entry("c_glutenFree", "false"),
			Map.entry("c_sugarFree", "false"),
			Map.entry("c_lactoseFree", "false"),
			Map.entry("c_dairyFree", "false"),
			Map.entry("c_suitableForVegetarians", "false"),
			Map.entry("c_suitableForVegans", "false"),
			Map.entry("c_healthy", "false"),
			Map.entry("c_novelty", "false")
	);

	private final RimiFeedReader reader = new RimiFeedReader();

	@Test
	void isNamedRimi() {
		assertThat(reader.getName()).isEqualTo("rimi");
		assertThat(reader.getVersion()).isEqualTo("1");
	}

	@Test
	void readsARowOfTheCatalogue() {
		var items = reader.read(CATALOGUE, feed(milk(Map.of(
				"title", "Oat drink, \"Barista\"",
				"title_LT", "Avižų gėrimas, \"Barista\"",
				"price_LT", "2.49",
				"c_organic", "true",
				"c_suitableForVegans", "true",
				"c_healthy", "true",
				"c_novelty", "true"
		)))).toList();

		assertThat(items).containsExactly(new FeedRecord(
				1,
				"1000001",
				Map.of("lt", "Avižų gėrimas, \"Barista\"", "en", "Oat drink, \"Barista\""),
				Map.of("brand", "Rokiškio", "countryOfOrigin", "LT"),
				List.of("organic", "suitableForVegans"),
				new BigDecimal("2.49"),
				"EUR"
		));
	}

	@Test
	void readsEveryDietaryFlagThatIsTrueAsATag() {
		var items = reader.read(CATALOGUE, feed(milk(Map.of(
				"c_organic", "true",
				"c_glutenFree", "true",
				"c_sugarFree", "true",
				"c_lactoseFree", "true",
				"c_dairyFree", "true",
				"c_suitableForVegetarians", "true",
				"c_suitableForVegans", "true"
		)))).toList();

		assertThat(items).extracting(item -> ((FeedRecord) item).tags()).containsExactly(List.of(
				"organic", "glutenFree", "sugarFree", "lactoseFree", "dairyFree", "suitableForVegetarians", "suitableForVegans"
		));
	}

	@Test
	void leavesOutTheValuesThatAreBlank() {
		var items = reader.read(CATALOGUE, feed(milk(Map.of("title", " ", "brand", "", "c_countryOfOrigin", "")))).toList();

		assertThat(items).containsExactly(
				new FeedRecord(1, "1000001", Map.of("lt", "Pienas ROKIŠKIO 2,5 %, 1 l"), Map.of(), List.of(), new BigDecimal("1.29"), "EUR")
		);
	}

	@Test
	void trimsTheValues() {
		var items = reader.read(CATALOGUE, feed(milk(Map.of(
				"item", " 1000001 ",
				"title", " Milk ",
				"title_LT", " Pienas ",
				"price_LT", " 1.29 ",
				"brand", " Rokiškio ",
				"c_countryOfOrigin", " LT ",
				"available_LT", " true ",
				"c_organic", " true "
		)))).toList();

		assertThat(items).containsExactly(new FeedRecord(
				1, "1000001", Map.of("lt", "Pienas", "en", "Milk"), Map.of("brand", "Rokiškio", "countryOfOrigin", "LT"), List.of("organic"),
				new BigDecimal("1.29"), "EUR"
		));
	}

	@Test
	void skipsTheRowsOfOtherCataloguesAndKeepsThePositionsOfTheFeed() {
		var items = reader.read(CATALOGUE, feed(
				milk(Map.of("item", "1000001")),
				milk(Map.of("item", "1000002", "title_LT", "", "title_LV", "Piens", "available_LV", "true")),
				milk(Map.of("item", "1000003"))
		)).toList();

		assertThat(items).extracting(item -> item.position() + ":" + ((FeedRecord) item).productKey()).containsExactly("1:1000001", "3:1000003");
	}

	@Test
	void skipsTheRowsThatAreNotAvailableWhateverTheirPrice() {
		var items = reader.read(CATALOGUE, feed(
				milk(Map.of("item", "1000001", "available_LT", "false")),
				milk(Map.of("item", "1000002", "available_LT", "false", "price_LT", "0.00")),
				milk(Map.of("item", "1000003", "available_LT", "true", "price_LT", "0.00"))
		)).toList();

		assertThat(items).containsExactly(
				new FeedRecord(3, "1000003", Map.of("lt", "Pienas ROKIŠKIO 2,5 %, 1 l", "en", "Milk ROKIŠKIO 2.5%, 1 l"),
						Map.of("brand", "Rokiškio", "countryOfOrigin", "LT"), List.of(), new BigDecimal("0.00"), "EUR")
		);
	}

	@Test
	void readsTheRowsOfTheFoodCategoriesOfEveryCountryAndTheRowsWithoutACategory() {
		var categories = List.of(
				"products > bread-and-pastries > bread",
				"products > vegan-and-vegetarian- > tofu",
				"products > -vikis-farm-store > cheese",
				"products > meat-fish-and-ready-to-eat-food",
				"products > talu-toidab---farmer-s-market > honey",
				"products > pre-order-festivity-meals > cakes",
				"produkti",
				"produktai",
				"tooted",
				""
		);

		var items = reader.read(CATALOGUE, feed(categories.stream().map(category -> milk(Map.of("category", category))).toArray(String[]::new))).toList();

		assertThat(items).hasSize(categories.size()).allMatch(FeedRecord.class::isInstance);
	}

	@Test
	void skipsTheRowsOfTheOtherCategories() {
		var items = reader.read(CATALOGUE, feed(
				milk(Map.of("item", "1000001", "category", "products > household-pet-and-leisure-items > books-and-magazines")),
				milk(Map.of("item", "1000002", "category", "products > drinks > juice")),
				milk(Map.of("item", "1000003", "category", "products > alcoholic-beverages > wine")),
				milk(Map.of("item", "1000004", "category", "products > sweets-and-snacks-for-pets > treats")),
				milk(Map.of("item", "1000005", "category", "products > dairy-products-eggs-cheese"))
		)).toList();

		assertThat(items).extracting(item -> ((FeedRecord) item).productKey()).containsExactly("1000005");
	}

	@Test
	void readsAValueOverSeveralLinesAsOneRow() {
		var items = reader.read(CATALOGUE, feed(
				milk(Map.of("item", "1000001", "description_LT", "Pasterizuotas\npienas,\n\"Rokiškio\"")),
				milk(Map.of("item", "1000002"))
		)).toList();

		assertThat(items).extracting(item -> item.position() + ":" + ((FeedRecord) item).productKey()).containsExactly("1:1000001", "2:1000002");
	}

	@Test
	void rejectsARowWhoseColumnsDoNotMatchTheHeaderAndGoesOn() {
		var items = reader.read(CATALOGUE, feed(
				milk(Map.of("item", "1000001")) + ",extra",
				"1000002,Milk",
				milk(Map.of("item", "1000003"))
		)).toList();

		assertThat(items).extracting(item -> item instanceof RejectedRecord rejected ? rejected : item.position()).containsExactly(
				new RejectedRecord(1, null, "The row does not have the columns of the header, columns: " + (HEADER.size() + 1)),
				new RejectedRecord(2, null, "The row does not have the columns of the header, columns: 2"),
				3L
		);
	}

	@Test
	void rejectsAPriceThatIsNotANumber() {
		var items = reader.read(CATALOGUE, feed(milk(Map.of("price_LT", "1,29")))).toList();

		assertThat(items).containsExactly(new RejectedRecord(1, "1000001", "The price is not a number"));
	}

	@Test
	void readsAMissingPriceAsNull() {
		var items = reader.read(CATALOGUE, feed(milk(Map.of("price_LT", "")))).toList();

		assertThat(items).extracting(item -> ((FeedRecord) item).price()).containsExactly((BigDecimal) null);
	}

	@Test
	void rejectsAnAvailabilityThatIsNeitherTrueNorFalse() {
		var items = reader.read(CATALOGUE, feed(milk(Map.of("available_LT", "yes")))).toList();

		assertThat(items).containsExactly(new RejectedRecord(1, "1000001", "The availability is neither true nor false"));
	}

	@Test
	void failsAFeedWithoutTheColumnsOfTheCountryOfTheCatalogue() {
		var estonian = ImmutableCatalogue.builder().from(CATALOGUE).id(new CatalogueIdImpl("rimi-ee")).country("EE").primaryLanguage("et").build();

		assertThatThrownBy(() -> reader.read(estonian, feed(milk(Map.of()))).toList())
				.isInstanceOf(UncheckedIOException.class)
				.hasMessage("The Feed lacks a column the adapter reads, column: title_EE");
	}

	@Test
	void failsAFeedWithoutAColumnOfEveryCountry() {
		var header = HEADER.stream().filter(column -> !column.equals("c_glutenFree")).toList();

		assertThatThrownBy(() -> reader.read(CATALOGUE, new ByteArrayInputStream(String.join(",", header).getBytes(UTF_8))).toList())
				.isInstanceOf(UncheckedIOException.class)
				.hasMessage("The Feed lacks a column the adapter reads, column: c_glutenFree");
	}

	@Test
	void failsAnEmptyFeed() {
		assertThatThrownBy(() -> reader.read(CATALOGUE, new ByteArrayInputStream(new byte[0])).toList())
				.isInstanceOf(UncheckedIOException.class)
				.hasMessage("The Feed lacks a column the adapter reads, column: item");
	}

	@Test
	void failsAFeedThatCannotBeReadOn() {
		var items = reader.read(CATALOGUE, feed(milk(Map.of("item", "1000001")), "\"1000002,Milk"));

		assertThatThrownBy(items::toList).isInstanceOf(UncheckedIOException.class);
	}

	@Test
	void hasNoDeletionFeeds() {
		assertThatThrownBy(() -> reader.readDeletions(CATALOGUE, feed()))
				.isInstanceOf(UnsupportedOperationException.class)
				.hasMessage("Rimi sends no Deletion Feeds");
	}

	@Test
	void readsAFeedLazily() {
		var items = reader.read(CATALOGUE, new LargeFeed(Long.MAX_VALUE)).limit(3).toList();

		assertThat(items).extracting(item -> ((FeedRecord) item).productKey()).containsExactly("1", "2", "3");
	}

	/**
	 * The heap of the tests is far smaller than the Feed, so this fails if the adapter holds the Feed in memory.
	 */
	@Test
	void readsALargeFeedInConstantMemory() {
		var feed = new LargeFeed(300L * 1024 * 1024);

		try (var items = reader.read(CATALOGUE, feed)) {
			assertThat(items.filter(FeedRecord.class::isInstance).count()).isEqualTo(feed.products);
		}
	}

	private static String milk(Map<String, String> values) {
		var row = new LinkedHashMap<>(MILK);
		row.putAll(values);
		return HEADER.stream().map(column -> csv(row.getOrDefault(column, ""))).collect(Collectors.joining(","));
	}

	private static String csv(String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static InputStream feed(String... rows) {
		return new ByteArrayInputStream(Stream.concat(Stream.of(String.join(",", HEADER)), Stream.of(rows)).collect(Collectors.joining("\n")).getBytes(UTF_8));
	}

	/**
	 * A Feed of rows of the Catalogue, each over a kilobyte, made as it is read.
	 */
	private static final class LargeFeed extends InputStream {
		private static final String DESCRIPTION = "Pasterizuotas pienas. ".repeat(40);

		private final long size;
		private long products;
		private long read;
		private byte[] row = (String.join(",", HEADER) + "\n").getBytes(UTF_8);
		private int next;

		/**
		 * @param size The size of the Feed in bytes, at least
		 */
		private LargeFeed(long size) {
			this.size = size;
		}

		@Override
		public int read() {
			if (next == row.length) {
				if (read >= size) {
					return -1;
				}
				row = (milk(Map.of("item", Long.toString(++products), "description_LT", DESCRIPTION)) + "\n").getBytes(UTF_8);
				next = 0;
			}
			read++;
			return row[next++] & 0xff;
		}
	}
}
