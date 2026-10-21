package eu.tealhelix.catalogue.tests;

import static io.restassured.RestAssured.given;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.path.json.JsonPath;

/**
 * Feeds in the format of the tests' adapter, {@code test-jsonl}, waiting for their runs to be processed, and reading the
 * runs back, through the endpoints as the caller of the test and from the database.
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

	/**
	 * A line of a Deletion Feed.
	 */
	static String deletion(String productKey) {
		try {
			return OBJECT_MAPPER.writeValueAsString(Map.of("productKey", productKey));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
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

	static JsonPath run(String runId) {
		return given().when().get("/api/catalogue/v1/ingestion-runs/" + runId).then().statusCode(200).extract().jsonPath();
	}

	static Map<String, Integer> counts(String runId) {
		return run(runId).getMap("counts", String.class, Integer.class);
	}

	/**
	 * The rejects of a run, each without its missing values.
	 */
	static List<Map<String, Object>> rejects(String runId) {
		List<Map<String, Object>> rejects = given().when().get("/api/catalogue/v1/ingestion-runs/" + runId + "/rejects?limit=100")
				.then().statusCode(200)
				.extract().jsonPath().getList("$");
		rejects.forEach(reject -> reject.values().removeIf(value -> value == null));
		return rejects;
	}

	static Timestamp timestamp(DataSource dataSource, String runId, String column) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT " + column + " FROM IN_INGESTION_RUN WHERE id = ?::uuid")) {
			statement.setString(1, runId);
			try (var resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getTimestamp(1);
			}
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
