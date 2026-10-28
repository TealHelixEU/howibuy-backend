package eu.tealhelix.catalogue.feeds.rimi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import eu.tealhelix.catalogue.feeds.DeletionItem;
import eu.tealhelix.catalogue.feeds.FeedItem;
import eu.tealhelix.catalogue.feeds.FeedReader;
import eu.tealhelix.catalogue.feeds.FeedRecord;
import eu.tealhelix.catalogue.feeds.RejectedRecord;
import eu.tealhelix.catalogue.v1.model.Catalogue;

/**
 * The adapter of Rimi's export, named {@value #NAME}: one CSV file for all of Rimi's countries, with a header row and one
 * row per product. The columns suffixed with a country, e.g. {@code title_LT}, are the product in the Catalogue of that
 * country; the unsuffixed ones are the product in the English e-shop of its home country, so they are the same in every
 * Catalogue. For the country {@code XX} of the Catalogue it reads:
 * <ul>
 * <li>the product key from {@code item};</li>
 * <li>the name in the primary language of the Catalogue from {@code title_XX}, and the English one from {@code title};</li>
 * <li>the price from {@code price_XX}, in euros;</li>
 * <li>the characteristics {@code brand} and {@code countryOfOrigin} from {@code brand} and {@code c_countryOfOrigin};</li>
 * <li>a tag for each of the dietary flags ({@code c_organic}, {@code c_glutenFree}, ...) that is {@code true}, named as
 *     the flag without its {@code c_}.</li>
 * </ul>
 * A row without {@code title_XX} is not the Catalogue's, and a row with {@code available_XX=false} is not on sale in it;
 * both are skipped. So is a row whose English {@code category} is a path ({@code products > <branch> > ...}) outside the
 * food branches; a row whose category is not a path is read. Every value is trimmed, and a blank one is left out. A row
 * whose columns do not match the header, or whose price or availability cannot be read, is rejected. A Feed without a
 * column that is read fails as a whole.
 */
@ApplicationScoped
public class RimiFeedReader implements FeedReader {
	public static final String NAME = "rimi";

	private static final ObjectReader CSV_READER = new CsvMapper().enable(CsvParser.Feature.WRAP_AS_ARRAY).readerFor(String[].class);
	private static final String CURRENCY = "EUR";
	private static final String ENGLISH = "en";
	private static final String FLAG_PREFIX = "c_";
	private static final List<String> DIETARY_FLAGS = List.of(
			"c_organic", "c_glutenFree", "c_sugarFree", "c_lactoseFree", "c_dairyFree", "c_suitableForVegetarians", "c_suitableForVegans"
	);
	/**
	 * The food branches of the English category trees of all of Rimi's countries; drinks and alcohol are left out.
	 */
	private static final Set<String> FOOD_BRANCHES = Set.of(
			// LV
			"bread-and-pastries", "dairy-and-eggs", "food-cupboard", "frozen-food", "fruit-and-vegetables", "made-in-rimi",
			"meat-fish-and-culinary", "sweets-and-snacks", "vegan-and-vegetarian-",
			// LT
			"-vikis-farm-store", "bread-products-and-confectionery", "dairy-products-eggs-cheese", "frozen-foods",
			"fruits-vegetables-and-flowers", "groceries", "meat-fish-and-ready-to-eat-food", "vegan-and-vegetarian",
			// EE
			"bread-pastry-confectionery", "fruits-vegetables-flowers", "meat-and-fish-products", "pre-order-festivity-meals",
			"prepared-food", "talu-toidab---farmer-s-market", "vegan-products"
	);

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getVersion() {
		return "1";
	}

	@Override
	public Stream<FeedItem> read(Catalogue catalogue, InputStream feed) {
		Iterator<String[]> rows = rows(feed);
		Columns columns = unchecked(() -> Columns.of(rows.hasNext() ? rows.next() : new String[0], catalogue.getCountry()));
		var position = new AtomicLong();
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(rows, Spliterator.ORDERED), false)
				.map(row -> read(position.incrementAndGet(), row, columns, catalogue))
				.flatMap(Optional::stream);
	}

	@Override
	public Stream<DeletionItem> readDeletions(Catalogue catalogue, InputStream feed) {
		throw new UnsupportedOperationException("Rimi sends no Deletion Feeds");
	}

	/**
	 * @return The record of the row, empty if the row is skipped
	 */
	private static Optional<FeedItem> read(long position, String[] row, Columns columns, Catalogue catalogue) {
		if (row.length != columns.count()) {
			return Optional.of(new RejectedRecord(position, null, "The row does not have the columns of the header, columns: " + row.length));
		}
		String title = value(row, columns.catalogueTitle());
		String available = value(row, columns.available());
		if (title == null || "false".equals(available) || !isFood(value(row, columns.category()))) {
			return Optional.empty();
		}
		String productKey = value(row, columns.item());
		if (!"true".equals(available)) {
			return Optional.of(new RejectedRecord(position, productKey, "The availability is neither true nor false"));
		}
		String price = value(row, columns.price());
		BigDecimal amount;
		try {
			amount = price != null ? new BigDecimal(price) : null;
		} catch (NumberFormatException e) {
			return Optional.of(new RejectedRecord(position, productKey, "The price is not a number"));
		}
		var names = new LinkedHashMap<String, String>();
		names.put(catalogue.getPrimaryLanguage(), title);
		putIfPresent(names, ENGLISH, value(row, columns.title()));
		var characteristics = new LinkedHashMap<String, String>();
		putIfPresent(characteristics, "brand", value(row, columns.brand()));
		putIfPresent(characteristics, "countryOfOrigin", value(row, columns.countryOfOrigin()));
		List<String> tags = columns.dietaryFlags().entrySet().stream()
				.filter(flag -> "true".equals(value(row, flag.getValue())))
				.map(flag -> flag.getKey().substring(FLAG_PREFIX.length()))
				.toList();
		return Optional.of(new FeedRecord(position, productKey, names, characteristics, tags, amount, CURRENCY));
	}

	/**
	 * A category that is not a path, e.g. {@code produktai}, names no branch, so it may be food.
	 */
	private static boolean isFood(String category) {
		if (category == null) return true;
		var path = category.split(">");
		return path.length < 2 || FOOD_BRANCHES.contains(path[1].trim());
	}

	private static void putIfPresent(Map<String, String> map, String key, String value) {
		if (value != null) {
			map.put(key, value);
		}
	}

	/**
	 * @return The trimmed value of the column, {@code null} if it is blank
	 */
	private static String value(String[] row, int column) {
		var value = row[column].trim();
		return value.isEmpty() ? null : value;
	}

	/**
	 * @return The rows of the Feed, read as they are iterated
	 */
	private static Iterator<String[]> rows(InputStream feed) {
		MappingIterator<String[]> rows = unchecked(() -> CSV_READER.readValues(feed));
		return new Iterator<>() {
			@Override
			public boolean hasNext() {
				return unchecked(rows::hasNextValue);
			}

			@Override
			public String[] next() {
				return unchecked(rows::nextValue);
			}
		};
	}

	private static <T> T unchecked(IoCall<T> call) {
		try {
			return call.call();
		} catch (IOException e) {
			throw new UncheckedIOException(e.getMessage(), e);
		}
	}

	@FunctionalInterface
	private interface IoCall<T> {
		T call() throws IOException;
	}

	/**
	 * Where the columns that are read are in the rows of a Feed, for the country of a Catalogue.
	 *
	 * @param count           The number of columns of the header
	 * @param dietaryFlags    The dietary flags, in the order of {@link #DIETARY_FLAGS}
	 * @param catalogueTitle  The title in the Catalogue
	 * @param available       Whether the product is on sale in the Catalogue
	 * @param price           The price in the Catalogue
	 */
	private record Columns(
			int count,
			int item,
			int title,
			int category,
			int brand,
			int countryOfOrigin,
			Map<String, Integer> dietaryFlags,
			int catalogueTitle,
			int available,
			int price
	) {
		static Columns of(String[] header, String country) throws IOException {
			var indexes = new HashMap<String, Integer>();
			for (int i = 0; i < header.length; i++) {
				indexes.putIfAbsent(header[i], i);
			}
			int item = index(indexes, "item");
			int title = index(indexes, "title");
			int category = index(indexes, "category");
			int brand = index(indexes, "brand");
			int countryOfOrigin = index(indexes, "c_countryOfOrigin");
			var dietaryFlags = new LinkedHashMap<String, Integer>();
			for (String flag : DIETARY_FLAGS) {
				dietaryFlags.put(flag, index(indexes, flag));
			}
			return new Columns(
					header.length,
					item,
					title,
					category,
					brand,
					countryOfOrigin,
					dietaryFlags,
					index(indexes, "title_" + country),
					index(indexes, "available_" + country),
					index(indexes, "price_" + country)
			);
		}

		private static int index(Map<String, Integer> indexes, String column) throws IOException {
			Integer index = indexes.get(column);
			if (index == null) {
				throw new IOException("The Feed lacks a column the adapter reads, column: " + column);
			}
			return index;
		}
	}
}
