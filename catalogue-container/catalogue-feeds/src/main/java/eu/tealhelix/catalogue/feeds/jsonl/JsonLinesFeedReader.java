package eu.tealhelix.catalogue.feeds.jsonl;

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
import eu.tealhelix.catalogue.feeds.DeletionItem;
import eu.tealhelix.catalogue.feeds.DeletionRecord;
import eu.tealhelix.catalogue.feeds.FeedItem;
import eu.tealhelix.catalogue.feeds.FeedReader;
import eu.tealhelix.catalogue.feeds.FeedRecord;
import eu.tealhelix.catalogue.feeds.RejectedRecord;
import eu.tealhelix.catalogue.v1.model.Catalogue;

/**
 * The adapter of a format of our own, named {@value #NAME}, for a Retailer that has none to offer: a Feed of JSON Lines,
 * one product per line, e.g.
 * <pre>
 * {"productKey": "p-1", "names": {"el": "Γάλα", "en": "Milk"}, "characteristics": {"fat": "2%"}, "tags": ["dairy"], "price": "1.29", "currency": "EUR"}
 * </pre>
 * A missing field is read as {@code null}, for the validation to reject. A line that is not a JSON object is rejected.
 * A Deletion Feed has the product key alone on each line, e.g. {@code {"productKey": "p-1"}}. A Feed is of one Catalogue,
 * so every record is read whatever the Catalogue.
 */
@ApplicationScoped
public class JsonLinesFeedReader implements FeedReader {
	public static final String NAME = "jsonl";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String NOT_AN_OBJECT = "The record is not a JSON object";

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
		var position = new AtomicLong();
		return new BufferedReader(new InputStreamReader(feed, UTF_8)).lines().map(line -> read(position.incrementAndGet(), line));
	}

	@Override
	public Stream<DeletionItem> readDeletions(Catalogue catalogue, InputStream feed) {
		var position = new AtomicLong();
		return new BufferedReader(new InputStreamReader(feed, UTF_8)).lines().map(line -> readDeletion(position.incrementAndGet(), line));
	}

	private static FeedItem read(long position, String line) {
		var product = readObject(line);
		if (product == null) {
			return new RejectedRecord(position, null, NOT_AN_OBJECT);
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

	private static DeletionItem readDeletion(long position, String line) {
		var deletion = readObject(line);
		if (deletion == null) {
			return new RejectedRecord(position, null, NOT_AN_OBJECT);
		}
		return new DeletionRecord(position, text(deletion.get("productKey")));
	}

	/**
	 * @return The JSON object of the line, {@code null} if the line is not one
	 */
	private static JsonNode readObject(String line) {
		try {
			var value = OBJECT_MAPPER.readTree(line);
			return value != null && value.isObject() ? value : null;
		} catch (JsonProcessingException e) {
			return null;
		}
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
