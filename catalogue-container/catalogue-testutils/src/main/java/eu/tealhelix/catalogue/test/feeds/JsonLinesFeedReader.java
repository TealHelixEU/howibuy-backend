package eu.tealhelix.catalogue.test.feeds;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.tealhelix.catalogue.feeds.FeedItem;
import eu.tealhelix.catalogue.feeds.FeedReader;
import eu.tealhelix.catalogue.feeds.FeedRecord;
import eu.tealhelix.catalogue.feeds.RejectedRecord;

/**
 * The adapter of the tests, named {@value #NAME}: a Feed of JSON Lines, one product per line, e.g.
 * <pre>
 * {"productKey": "p-1", "names": {"el": "Γάλα", "en": "Milk"}, "characteristics": {"fat": "2%"}, "tags": ["dairy"], "price": "1.29", "currency": "EUR"}
 * </pre>
 * A missing field is read as {@code null}, for the validation to reject. A line that is not a JSON object is rejected.
 */
@ApplicationScoped
public class JsonLinesFeedReader implements FeedReader {
	public static final String NAME = "test-jsonl";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getVersion() {
		return "1";
	}

	@Override
	public Stream<FeedItem> read(InputStream feed) {
		var position = new AtomicLong();
		return new BufferedReader(new InputStreamReader(feed, UTF_8)).lines().map(line -> read(position.incrementAndGet(), line));
	}

	private static FeedItem read(long position, String line) {
		JsonNode product;
		try {
			product = OBJECT_MAPPER.readTree(line);
		} catch (JsonProcessingException e) {
			product = null;
		}
		if (product == null || !product.isObject()) {
			return new RejectedRecord(position, null, "The record is not a JSON object");
		}
		var productKey = text(product.get("productKey"));
		BigDecimal price;
		try {
			price = product.hasNonNull("price") ? new BigDecimal(product.get("price").asText()) : null;
		} catch (NumberFormatException e) {
			return new RejectedRecord(position, productKey, "The price is not a number");
		}
		return new FeedRecord(
				position,
				productKey,
				strings(product.get("names")),
				strings(product.get("characteristics")),
				texts(product.get("tags")),
				price,
				text(product.get("currency"))
		);
	}

	private static List<String> texts(JsonNode array) {
		if (array == null || array.isNull()) return null;
		var texts = new ArrayList<String>();
		array.forEach(value -> texts.add(text(value)));
		return texts;
	}

	private static Map<String, String> strings(JsonNode object) {
		if (object == null || object.isNull()) return null;
		var strings = new LinkedHashMap<String, String>();
		object.properties().forEach(property -> strings.put(property.getKey(), text(property.getValue())));
		return strings;
	}

	private static String text(JsonNode value) {
		return value == null || value.isNull() ? null : value.asText();
	}
}
