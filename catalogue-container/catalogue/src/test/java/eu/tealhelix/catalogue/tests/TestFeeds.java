package eu.tealhelix.catalogue.tests;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Feeds in the format of the tests' adapter, {@code test-jsonl}, and waiting for their runs to be processed.
 */
final class TestFeeds {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final Duration WAIT = Duration.ofSeconds(30);

	private TestFeeds() {
	}

	/**
	 * A line of a Feed: a product with its characteristics and tags, on sale in euros.
	 */
	static String product(String productKey, Map<String, String> names, Map<String, String> characteristics, List<String> tags, String price) {
		var product = new LinkedHashMap<String, Object>();
		product.put("productKey", productKey);
		product.put("names", names);
		product.put("characteristics", characteristics);
		product.put("tags", tags);
		product.put("price", price);
		product.put("currency", "EUR");
		try {
			return OBJECT_MAPPER.writeValueAsString(product);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * A line of a Feed: a product without characteristics and tags, on sale in euros.
	 */
	static String product(String productKey, Map<String, String> names, String price) {
		return product(productKey, names, Map.of(), List.of(), price);
	}

	static String feed(String... lines) {
		return String.join("\n", lines) + "\n";
	}

	/**
	 * Wait until a run is processed.
	 *
	 * @return The status it ended with
	 */
	static String awaitTheEndOf(DataSource dataSource, String runId) throws SQLException, InterruptedException {
		var deadline = Instant.now().plus(WAIT);
		while (true) {
			var status = statusOf(dataSource, runId);
			if (!status.equals("RECEIVED") && !status.equals("PROCESSING")) return status;
			if (Instant.now().isAfter(deadline)) throw new AssertionError("The run was not processed in time, status: " + status);
			Thread.sleep(50);
		}
	}

	private static String statusOf(DataSource dataSource, String runId) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT status FROM IN_INGESTION_RUN WHERE id = ?::uuid")) {
			statement.setString(1, runId);
			try (var resultSet = statement.executeQuery()) {
				if (!resultSet.next()) throw new AssertionError("No such run, runId: " + runId);
				return resultSet.getString(1);
			}
		}
	}
}
