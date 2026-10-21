package eu.tealhelix.cataloguesync.e2e;

import static eu.tealhelix.cataloguesync.e2e.DeployableProcess.freePort;
import static eu.tealhelix.cataloguesync.e2e.DeployableProcess.requireSystemProperty;
import static eu.tealhelix.common.test.quarkus.PostgresAndKeycloakTestResource.KEYCLOAK_PORT;
import static eu.tealhelix.common.test.testcontainers.DockerImageNames.KEYCLOAK_IMAGE_TEALHELIX;
import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE_TEALHELIX;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import eu.tealhelix.common.test.quarkus.ServiceAccount;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The whole pipeline: a Retailer pushes Snapshots of its Catalogue to the Ingestion, the Catalogue Sync carries the
 * Product Changes into HowiBuy, and HowiBuy has the products and their Listings. The Ingestion, HowiBuy and the
 * Catalogue Sync run from the jars the build made, against a real Postgres and a real Keycloak. Both services seed the
 * development Retailer and its Catalogue from their {@code dev} Liquibase context.
 */
class CatalogueSyncIT {
	private static final String CATALOGUE_ID = "lime-fresh-gr";
	private static final Duration WAIT = Duration.ofSeconds(30);
	/** Small enough that a Snapshot spans several pages. */
	private static final int PAGE_SIZE = 4;

	private static Network network;
	private static PostgreSQLContainer postgres;
	private static GenericContainer<?> keycloak;
	private static DeployableProcess ingestion;
	private static DeployableProcess howiBuy;
	private static String realmUrl;
	private static String ingestionUrl;
	private static String howiBuyUrl;

	@BeforeAll
	static void startThePipeline() throws IOException, InterruptedException {
		network = Network.newNetwork();
		postgres = new PostgreSQLContainer(POSTGRES_IMAGE_TEALHELIX).withNetwork(network).withNetworkAliases("postgresql")
				.withDatabaseName("tealhelix").withUsername("postgres");
		postgres.start();
		keycloak = new GenericContainer<>(KEYCLOAK_IMAGE_TEALHELIX).withNetwork(network).withExposedPorts(KEYCLOAK_PORT);
		keycloak.start();
		realmUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(KEYCLOAK_PORT) + "/realms/tealhelix";

		var ingestionPort = freePort();
		ingestionUrl = "http://localhost:" + ingestionPort;
		ingestion = DeployableProcess.start("ingestion", "ingestion", Map.of(
				"quarkus.http.port", String.valueOf(ingestionPort),
				"quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
				"quarkus.datasource.reactive.url", postgres.getJdbcUrl().replace("jdbc:", "vertx-reactive:"),
				"quarkus.datasource.username", "th_catalogue",
				"quarkus.datasource.password", "th_catalogue",
				"quarkus.liquibase.contexts", "dev",
				"quarkus.oidc.auth-server-url", realmUrl,
				"catalogue.feed-store.directory", requireSystemProperty("e2e.feed-store.directory"),
				"catalogue.eviction.cron", "off"
		));
		var howiBuyPort = freePort();
		howiBuyUrl = "http://localhost:" + howiBuyPort;
		howiBuy = DeployableProcess.start("howibuy", "howibuy", Map.of(
				"quarkus.http.port", String.valueOf(howiBuyPort),
				"quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
				"quarkus.datasource.reactive.url", postgres.getJdbcUrl().replace("jdbc:", "vertx-reactive:"),
				"quarkus.datasource.username", "th_howibuy",
				"quarkus.datasource.password", "th_howibuy",
				"quarkus.liquibase.contexts", "dev,appdata",
				"config.jwk.url", realmUrl + "/protocol/openid-connect/certs",
				"config.jwt.expectedIssuer", realmUrl
		));
		ingestion.awaitReady(ingestionUrl + "/api/catalogue/v1/version");
		howiBuy.awaitReady(howiBuyUrl + "/api/howibuy/v1/version");
	}

	@AfterAll
	static void stopThePipeline() throws InterruptedException {
		if (howiBuy != null) howiBuy.close();
		if (ingestion != null) ingestion.close();
		if (keycloak != null) keycloak.stop();
		if (postgres != null) postgres.stop();
		if (network != null) network.close();
	}

	@Test
	void theSyncCarriesTheSnapshotsOfARetailerIntoHowiBuy() throws Exception {
		assertThat(pushSnapshot("lime-fresh-gr-snapshot-1.jsonl")).isEqualTo("DONE");

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
		assertThat(pushSnapshot("lime-fresh-gr-snapshot-2.jsonl")).isEqualTo("DONE");

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
	 * Push a Snapshot of the Catalogue as its Retailer, and wait until the Ingestion has processed it.
	 *
	 * @return The status its run ended with
	 */
	private static String pushSnapshot(String feed) throws IOException, InterruptedException {
		var accessToken = ServiceAccount.RETAILER.accessToken(keycloak);
		String location;
		try (InputStream body = CatalogueSyncIT.class.getResourceAsStream("/feeds/" + feed)) {
			location = given()
					.header("Authorization", "Bearer " + accessToken)
					.contentType("application/x-ndjson")
					.body(body.readAllBytes())
					.when().post(ingestionUrl + "/api/catalogue/v1/catalogues/" + CATALOGUE_ID + "/feeds?type=SNAPSHOT")
					.then().statusCode(202)
					.extract().header("Location");
		}
		var deadline = Instant.now().plus(WAIT);
		while (true) {
			String status = given()
					.header("Authorization", "Bearer " + accessToken)
					.when().get(location)
					.then().statusCode(200)
					.extract().path("status");
			if (!status.equals("RECEIVED") && !status.equals("PROCESSING")) return status;
			if (Instant.now().isAfter(deadline)) throw new AssertionError("The run was not processed in time, status: " + status);
			Thread.sleep(100);
		}
	}

	/**
	 * Run the {@code sync} command of the Catalogue Sync to its end.
	 *
	 * @return What it printed
	 */
	private static String sync(String name) throws IOException, InterruptedException {
		try (var sync = DeployableProcess.start("catalogue-sync", name, Map.of(
				"quarkus.rest-client.ingestion.url", ingestionUrl,
				"quarkus.rest-client.howibuy.url", howiBuyUrl,
				"quarkus.oidc-client.auth-server-url", realmUrl,
				"quarkus.oidc-client.credentials.secret", ServiceAccount.CATALOGUE_SYNC.clientSecret(),
				"catalogue-sync.page-size", String.valueOf(PAGE_SIZE)
		), "sync")) {
			var exitCode = sync.awaitExit();
			var output = sync.output();
			assertThat(exitCode).as("The exit code of the sync, which printed:%n%s", output).isZero();
			return output;
		}
	}

	private static long howiBuyCursor() throws SQLException {
		try (var connection = howiBuyConnection(); var statement = connection.createStatement();
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
		try (var connection = howiBuyConnection(); var statement = connection.prepareStatement(sql)) {
			statement.setString(1, CATALOGUE_ID);
			try (var resultSet = statement.executeQuery()) {
				var listings = new ArrayList<Listing>();
				while (resultSet.next()) {
					listings.add(new Listing(resultSet.getString(1), resultSet.getString(2), resultSet.getBigDecimal(3).setScale(2).toPlainString(), resultSet.getBoolean(4)));
				}
				return listings;
			}
		}
	}

	private static Connection howiBuyConnection() throws SQLException {
		return DriverManager.getConnection(postgres.getJdbcUrl(), "th_howibuy", "th_howibuy");
	}

	/**
	 * A Listing in HowiBuy, with the Greek name of its product and its price in euros.
	 */
	private record Listing(String productKey, String name, String price, boolean active) {
	}
}
