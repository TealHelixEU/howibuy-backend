package eu.tealhelix.catalogue.tests;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * A Catalogue of Lime Fresh for one test alone (primary language {@code el}, also {@code en}), in the format of the
 * tests' adapter, and what the test reads back of its products. A Snapshot takes the Listings it does not carry off sale,
 * so tests that shared a Catalogue would take each other's products off sale. The products of a Retailer are shared by
 * its Catalogues, so the products of the test are named apart by a prefix of their keys. Read with plain JDBC: the tests
 * check what the Ingestion stored in the database.
 */
final class TestCatalogue {
	static final String LIME_FRESH = "dc73176c-7069-4a4f-a74f-d60c4a5ec1ee";

	private final DataSource dataSource;
	private final String id;
	private final String prefix;

	private TestCatalogue(DataSource dataSource, String id, String prefix) {
		this.dataSource = dataSource;
		this.id = id;
		this.prefix = prefix;
	}

	static TestCatalogue create(DataSource dataSource) throws SQLException {
		var name = UUID.randomUUID().toString().substring(0, 8);
		var catalogue = new TestCatalogue(dataSource, "test-" + name, name + "-");
		catalogue.execute("INSERT INTO IN_CATALOGUE (id, retailer_id, country, primary_language, accepted_languages, adapter) VALUES "
				+ "('" + catalogue.id + "', '" + LIME_FRESH + "', 'GR', 'el', '{el,en}', 'test-jsonl')");
		return catalogue;
	}

	/**
	 * Remove the Catalogue with its runs, and the products of the test. Its runs must have been processed.
	 */
	void remove() throws SQLException {
		execute("DELETE FROM IN_LISTING WHERE catalogue_id = '" + id + "'");
		execute("DELETE FROM IN_INGESTION_REJECT WHERE ingestion_run_id IN (SELECT id FROM IN_INGESTION_RUN WHERE catalogue_id = '" + id + "')");
		execute("DELETE FROM IN_INGESTION_RUN WHERE catalogue_id = '" + id + "'");
		execute("DELETE FROM IN_CATALOGUE WHERE id = '" + id + "'");
		execute("DELETE FROM IN_RETAILER_PRODUCT_NAME WHERE retailer_product_id IN (SELECT id FROM IN_RETAILER_PRODUCT WHERE product_key LIKE '" + prefix + "%')");
		execute("DELETE FROM IN_RETAILER_PRODUCT WHERE product_key LIKE '" + prefix + "%'");
	}

	String id() {
		return id;
	}

	/**
	 * The key of a product of the test.
	 */
	String key(String name) {
		return prefix + name;
	}

	/**
	 * The change sequences of the test's products, by product key.
	 */
	Map<String, Long> changeSequences() throws SQLException {
		var changeSequences = new HashMap<String, Long>();
		try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT product_key, change_sequence FROM IN_RETAILER_PRODUCT WHERE product_key LIKE ?")) {
			statement.setString(1, prefix + "%");
			try (var resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					changeSequences.put(resultSet.getString(1), resultSet.getLong(2));
				}
			}
		}
		return changeSequences;
	}

	String inputHash(String productKey) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT input_hash FROM IN_RETAILER_PRODUCT WHERE product_key = ?")) {
			statement.setString(1, productKey);
			try (var resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getString(1);
			}
		}
	}

	Map<String, String> names(String productKey) throws SQLException {
		var names = new HashMap<String, String>();
		try (
				var connection = dataSource.getConnection();
				var statement = connection.prepareStatement("""
						SELECT n.lang, n.name
						FROM IN_RETAILER_PRODUCT_NAME n JOIN IN_RETAILER_PRODUCT p ON p.id = n.retailer_product_id
						WHERE p.product_key = ?
						""")
		) {
			statement.setString(1, productKey);
			try (var resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					names.put(resultSet.getString(1), resultSet.getString(2));
				}
			}
		}
		return names;
	}

	record Listing(BigDecimal price, String currency, boolean active, String lastSeenRunId) {
	}

	/**
	 * The Listing of a product in this Catalogue.
	 */
	Listing listing(String productKey) throws SQLException {
		try (
				var connection = dataSource.getConnection();
				var statement = connection.prepareStatement("""
						SELECT l.price, l.currency, l.active, l.last_seen_run_id
						FROM IN_LISTING l JOIN IN_RETAILER_PRODUCT p ON p.id = l.retailer_product_id
						WHERE p.product_key = ? AND l.catalogue_id = ?
						""")
		) {
			statement.setString(1, productKey);
			statement.setString(2, id);
			try (var resultSet = statement.executeQuery()) {
				resultSet.next();
				return new Listing(resultSet.getBigDecimal(1), resultSet.getString(2), resultSet.getBoolean(3), resultSet.getString(4));
			}
		}
	}

	private void execute(String sql) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}
}
