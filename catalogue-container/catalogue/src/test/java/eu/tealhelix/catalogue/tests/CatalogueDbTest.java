package eu.tealhelix.catalogue.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import eu.tealhelix.catalogue.test.quarkus.WithCatalogueDb;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * The Ingestion deployable boots on its own database, with the Retailers and Catalogues it serves seeded.
 */
@QuarkusTest
@WithCatalogueDb
class CatalogueDbTest {
	@Inject
	DataSource dataSource;

	@Test
	void findsTheSeededCatalogues() throws SQLException {
		var catalogues = new ArrayList<List<String>>();
		try (
				var connection = dataSource.getConnection();
				var statement = connection.createStatement();
				// IN_RETAILER has no entity, therefore, JDBC
				var resultSet = statement.executeQuery("""
						SELECT c.id, r.name, c.country, c.primary_language, array_to_string(c.accepted_languages, ',') AS accepted_languages, c.adapter, c.active
						FROM IN_CATALOGUE c JOIN IN_RETAILER r ON r.id = c.retailer_id
						ORDER BY c.id
						""")
		) {
			while (resultSet.next()) {
				catalogues.add(List.of(
						resultSet.getString("id"),
						resultSet.getString("name"),
						resultSet.getString("country"),
						resultSet.getString("primary_language"),
						resultSet.getString("accepted_languages"),
						resultSet.getString("adapter"),
						String.valueOf(resultSet.getBoolean("active"))
				));
			}
		}

		assertThat(catalogues).containsExactly(List.of("lime-fresh-gr", "Lime Fresh", "GR", "el", "el,en", "jsonl", "true"));
	}
}
