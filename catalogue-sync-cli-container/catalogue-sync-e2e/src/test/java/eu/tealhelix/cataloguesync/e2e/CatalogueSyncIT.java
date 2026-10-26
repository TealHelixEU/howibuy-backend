package eu.tealhelix.cataloguesync.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The whole pipeline: a Retailer pushes Snapshots of its Catalogue to the Ingestion, the Catalogue Sync carries the
 * Product Changes into HowiBuy, and HowiBuy has the products and their Listings. The Ingestion, HowiBuy and the
 * Catalogue Sync run from the jars the build made, against a real Postgres and a real Keycloak. Both services seed the
 * development Retailer and its Catalogue from their {@code dev} Liquibase context.
 */
class CatalogueSyncIT {
	/** Small enough that a Snapshot spans several pages. */
	private static final int PAGE_SIZE = 4;

	private static final Pipeline pipeline = new Pipeline("catalogue-sync");

	@BeforeAll
	static void startThePipeline() throws IOException, InterruptedException {
		pipeline.start(Map.of());
	}

	@AfterAll
	static void stopThePipeline() throws InterruptedException {
		pipeline.close();
	}

	@Test
	void theSyncCarriesTheSnapshotsOfARetailerIntoHowiBuy() throws Exception {
		assertThat(pipeline.pushSnapshot("lime-fresh-gr-snapshot-1.jsonl")).isEqualTo("DONE");

		assertThat(sync("sync-1")).contains("pages: 3, products: 11, cursor: " + howiBuyCursor());
		assertThat(listings()).containsExactlyInAnyOrder(
				new Listing("e2e-bread", "Ψωμί χωριάτικο", "1.10", true),
				new Listing("e2e-chicken", "Κοτόπουλο ολόκληρο", "6.30", true),
				new Listing("e2e-eggs", "Αυγά ελευθέρας βοσκής", "2.60", true),
				new Listing("e2e-feta", "Φέτα", "4.80", true),
				new Listing("e2e-honey", "Μέλι θυμαρίσιο", "7.40", true),
				new Listing("e2e-lentils", "Φακές", "1.70", true),
				new Listing("e2e-milk", "Γάλα φρέσκο", "1.29", true),
				new Listing("e2e-olive-oil", "Ελαιόλαδο εξαιρετικό παρθένο", "9.90", true),
				new Listing("e2e-oranges", "Πορτοκάλια", "1.20", true),
				new Listing("e2e-tomatoes", "Ντομάτες", "1.95", true),
				new Listing("e2e-yoghurt", "Γιαούρτι στραγγιστό", "2.15", true)
		);

		// The oranges are missing from the second Snapshot, and the milk costs more
		assertThat(pipeline.pushSnapshot("lime-fresh-gr-snapshot-2.jsonl")).isEqualTo("DONE");

		assertThat(sync("sync-2")).contains("pages: 1, products: 2, cursor: " + howiBuyCursor());
		assertThat(listings()).containsExactlyInAnyOrder(
				new Listing("e2e-bread", "Ψωμί χωριάτικο", "1.10", true),
				new Listing("e2e-chicken", "Κοτόπουλο ολόκληρο", "6.30", true),
				new Listing("e2e-eggs", "Αυγά ελευθέρας βοσκής", "2.60", true),
				new Listing("e2e-feta", "Φέτα", "4.80", true),
				new Listing("e2e-honey", "Μέλι θυμαρίσιο", "7.40", true),
				new Listing("e2e-lentils", "Φακές", "1.70", true),
				new Listing("e2e-milk", "Γάλα φρέσκο", "1.39", true),
				new Listing("e2e-olive-oil", "Ελαιόλαδο εξαιρετικό παρθένο", "9.90", true),
				new Listing("e2e-oranges", "Πορτοκάλια", "1.20", false),
				new Listing("e2e-tomatoes", "Ντομάτες", "1.95", true),
				new Listing("e2e-yoghurt", "Γιαούρτι στραγγιστό", "2.15", true)
		);
	}

	/**
	 * Run the {@code sync} command of the Catalogue Sync to its end.
	 *
	 * @return What it printed
	 */
	private static String sync(String name) throws IOException, InterruptedException {
		return pipeline.runCatalogueSync(name, Map.of("catalogue-sync.page-size", String.valueOf(PAGE_SIZE)), "sync");
	}

	private static long howiBuyCursor() throws SQLException {
		try (var connection = pipeline.howiBuyConnection(); var statement = connection.createStatement();
				var resultSet = statement.executeQuery("SELECT last_sequence FROM TH_CATALOGUE_CHANGE_CURSOR")) {
			resultSet.next();
			return resultSet.getLong(1);
		}
	}

	/**
	 * @return The Listings of the Catalogue in HowiBuy, each with its product
	 */
	private static List<Listing> listings() throws SQLException {
		var sql = """
				SELECT p.product_key, n.name, l.price, l.active
				FROM TH_LISTING l
				JOIN TH_RETAILER_PRODUCT p ON p.id = l.retailer_product_id
				JOIN TH_RETAILER_PRODUCT_NAME n ON n.retailer_product_id = p.id AND n.lang = 'el'
				WHERE l.catalogue_id = ?""";
		try (var connection = pipeline.howiBuyConnection(); var statement = connection.prepareStatement(sql)) {
			statement.setString(1, Pipeline.CATALOGUE_ID);
			try (var resultSet = statement.executeQuery()) {
				var listings = new ArrayList<Listing>();
				while (resultSet.next()) {
					listings.add(new Listing(resultSet.getString(1), resultSet.getString(2), resultSet.getBigDecimal(3).setScale(2).toPlainString(), resultSet.getBoolean(4)));
				}
				return listings;
			}
		}
	}

	/**
	 * A Listing in HowiBuy, with the Greek name of its product and its price in euros.
	 */
	private record Listing(String productKey, String name, String price, boolean active) {
	}
}
