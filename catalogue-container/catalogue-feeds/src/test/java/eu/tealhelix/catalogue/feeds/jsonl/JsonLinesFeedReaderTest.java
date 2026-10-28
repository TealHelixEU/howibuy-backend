package eu.tealhelix.catalogue.feeds.jsonl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.tealhelix.catalogue.feeds.DeletionRecord;
import eu.tealhelix.catalogue.feeds.FeedRecord;
import eu.tealhelix.catalogue.feeds.RejectedRecord;
import eu.tealhelix.catalogue.v1.model.Catalogue;
import eu.tealhelix.catalogue.v1.model.ImmutableCatalogue;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import org.junit.jupiter.api.Test;

class JsonLinesFeedReaderTest {
	private static final Catalogue CATALOGUE = ImmutableCatalogue.builder()
			.id(new CatalogueIdImpl("kritis-gr"))
			.retailerId(new GenericRetailerId("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11"))
			.country("GR")
			.primaryLanguage("el")
			.acceptedLanguages(Set.of("el", "en"))
			.adapter(JsonLinesFeedReader.NAME)
			.isActive(true)
			.build();

	private final JsonLinesFeedReader reader = new JsonLinesFeedReader();

	@Test
	void isNamedJsonl() {
		assertThat(reader.getName()).isEqualTo("jsonl");
		assertThat(reader.getVersion()).isEqualTo("1");
	}

	@Test
	void readsEachLineAsAProduct() {
		var items = reader.read(CATALOGUE, feed(
				"{\"productKey\": \"p-1\", \"names\": {\"el\": \"Γάλα\", \"en\": \"Milk\"}, \"characteristics\": {\"fat\": \"2%\"}, \"tags\": [\"dairy\", \"fresh\"], \"price\": \"1.29\", \"currency\": \"EUR\"}",
				"{\"productKey\": \"p-2\", \"names\": {\"el\": \"Ψωμί\"}, \"characteristics\": {}, \"tags\": [], \"price\": 0.95, \"currency\": \"EUR\"}"
		)).toList();

		assertThat(items).containsExactly(
				new FeedRecord(1, "p-1", Map.of("el", "Γάλα", "en", "Milk"), Map.of("fat", "2%"), List.of("dairy", "fresh"), new BigDecimal("1.29"), "EUR"),
				new FeedRecord(2, "p-2", Map.of("el", "Ψωμί"), Map.of(), List.of(), new BigDecimal("0.95"), "EUR")
		);
	}

	@Test
	void readsAMissingFieldAsNull() {
		var items = reader.read(CATALOGUE, feed("{\"productKey\": \"p-1\"}")).toList();

		assertThat(items).containsExactly(new FeedRecord(1, "p-1", null, null, null, null, null));
	}

	@Test
	void rejectsALineThatIsNotAJsonObjectAndGoesOn() {
		var items = reader.read(CATALOGUE, feed(
				"not json",
				"[\"p-1\"]",
				"{\"productKey\": \"p-3\"}"
		)).toList();

		assertThat(items).containsExactly(
				new RejectedRecord(1, null, "The record is not a JSON object"),
				new RejectedRecord(2, null, "The record is not a JSON object"),
				new FeedRecord(3, "p-3", null, null, null, null, null)
		);
	}

	@Test
	void rejectsAPriceThatIsNotANumber() {
		var items = reader.read(CATALOGUE, feed("{\"productKey\": \"p-1\", \"price\": \"cheap\"}")).toList();

		assertThat(items).containsExactly(new RejectedRecord(1, "p-1", "The price is not a number"));
	}

	@Test
	void readsTheProductKeyOfEachLineOfADeletionFeed() {
		var items = reader.readDeletions(CATALOGUE, feed(
				"{\"productKey\": \"p-1\"}",
				"not json",
				"{\"productKey\": \"p-3\"}"
		)).toList();

		assertThat(items).containsExactly(
				new DeletionRecord(1, "p-1"),
				new RejectedRecord(2, null, "The record is not a JSON object"),
				new DeletionRecord(3, "p-3")
		);
	}

	@Test
	void readsAFeedLazily() {
		var items = reader.read(CATALOGUE, endlessFeed()).limit(3).toList();

		assertThat(items).extracting(item -> ((FeedRecord) item).productKey()).containsExactly("p-1", "p-2", "p-3");
	}

	private static InputStream feed(String... lines) {
		return new ByteArrayInputStream(String.join("\n", lines).getBytes(UTF_8));
	}

	/**
	 * @return A Feed that never ends, so that reading all of it before the first item never returns
	 */
	private static InputStream endlessFeed() {
		return new InputStream() {
			private long products;
			private byte[] line = new byte[0];
			private int next;

			@Override
			public int read() {
				if (next == line.length) {
					line = ("{\"productKey\": \"p-" + ++products + "\"}\n").getBytes(UTF_8);
					next = 0;
				}
				return line[next++];
			}
		};
	}
}
