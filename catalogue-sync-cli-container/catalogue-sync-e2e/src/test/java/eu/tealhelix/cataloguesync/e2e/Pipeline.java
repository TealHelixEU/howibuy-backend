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
import java.util.HashMap;
import java.util.Map;

import eu.tealhelix.common.test.quarkus.ServiceAccount;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The Ingestion and HowiBuy, running from the jars the build made against a real Postgres and a real Keycloak, and the
 * Catalogue Sync run between them. Both services seed the development Retailer and its Catalogue from their {@code dev}
 * Liquibase context.
 */
final class Pipeline implements AutoCloseable {
	static final String CATALOGUE_ID = "lime-fresh-gr";
	private static final Duration WAIT = Duration.ofSeconds(30);

	private final String name;
	private Network network;
	private PostgreSQLContainer postgres;
	private GenericContainer<?> keycloak;
	private DeployableProcess ingestion;
	private DeployableProcess howiBuy;
	private String realmUrl;
	private String ingestionUrl;
	private String howiBuyUrl;

	/**
	 * @param name The name of the pipeline, which prefixes the names of the log files of its processes
	 */
	Pipeline(String name) {
		this.name = name;
	}

	/**
	 * Start the pipeline, and wait until both services are ready. {@link #close()} stops what it started, also when it
	 * fails to start.
	 *
	 * @param howiBuyConfig System properties of HowiBuy, besides those that connect it to the database and the IDM
	 */
	void start(Map<String, String> howiBuyConfig) throws IOException, InterruptedException {
		network = Network.newNetwork();
		postgres = new PostgreSQLContainer(POSTGRES_IMAGE_TEALHELIX).withNetwork(network).withNetworkAliases("postgresql")
				.withDatabaseName("tealhelix").withUsername("postgres");
		postgres.start();
		keycloak = new GenericContainer<>(KEYCLOAK_IMAGE_TEALHELIX).withNetwork(network).withExposedPorts(KEYCLOAK_PORT);
		keycloak.start();
		realmUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(KEYCLOAK_PORT) + "/realms/tealhelix";

		var ingestionPort = freePort();
		ingestionUrl = "http://localhost:" + ingestionPort;
		ingestion = DeployableProcess.start("ingestion", name + "-ingestion", Map.of(
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
		var config = new HashMap<>(howiBuyConfig);
		config.putAll(Map.of(
				"quarkus.http.port", String.valueOf(howiBuyPort),
				"quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
				"quarkus.datasource.reactive.url", postgres.getJdbcUrl().replace("jdbc:", "vertx-reactive:"),
				"quarkus.datasource.username", "th_howibuy",
				"quarkus.datasource.password", "th_howibuy",
				"quarkus.liquibase.contexts", "dev,appdata",
				"config.jwk.url", realmUrl + "/protocol/openid-connect/certs",
				"config.jwt.expectedIssuer", realmUrl
		));
		howiBuy = DeployableProcess.start("howibuy", name + "-howibuy", config);
		ingestion.awaitReady(ingestionUrl + "/api/catalogue/v1/version");
		howiBuy.awaitReady(howiBuyUrl + "/api/howibuy/v1/version");
	}

	/**
	 * Push a Snapshot of the Catalogue as its Retailer, and wait until the Ingestion has processed it.
	 *
	 * @param feed The Feed, a resource under {@code /feeds}
	 * @return The status its run ended with
	 */
	String pushSnapshot(String feed) throws IOException, InterruptedException {
		var accessToken = accessToken(ServiceAccount.RETAILER);
		String location;
		try (InputStream body = Pipeline.class.getResourceAsStream("/feeds/" + feed)) {
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
	 * Run a command of the Catalogue Sync to its end, and require that it exits with {@code 0}.
	 *
	 * @param process The name of the process, which names its log file
	 * @param config  System properties of the Catalogue Sync, besides those that connect it to the services and the IDM
	 * @param command The command and its options
	 * @return What it printed
	 */
	String runCatalogueSync(String process, Map<String, String> config, String... command) throws IOException, InterruptedException {
		var allConfig = new HashMap<>(config);
		allConfig.putAll(Map.of(
				"quarkus.rest-client.ingestion.url", ingestionUrl,
				"quarkus.rest-client.howibuy.url", howiBuyUrl,
				"quarkus.oidc-client.auth-server-url", realmUrl,
				"quarkus.oidc-client.credentials.secret", ServiceAccount.CATALOGUE_SYNC.clientSecret()
		));
		try (var catalogueSync = DeployableProcess.start("catalogue-sync", name + "-" + process, allConfig, command)) {
			var exitCode = catalogueSync.awaitExit();
			var output = catalogueSync.output();
			assertThat(exitCode).as("The exit code of the Catalogue Sync, which printed:%n%s", output).isZero();
			return output;
		}
	}

	/**
	 * @return An access token of the service account, from the IDM of the pipeline
	 */
	String accessToken(ServiceAccount account) {
		return account.accessToken(keycloak);
	}

	/**
	 * @return The URL HowiBuy listens to
	 */
	String howiBuyUrl() {
		return howiBuyUrl;
	}

	Connection howiBuyConnection() throws SQLException {
		return DriverManager.getConnection(postgres.getJdbcUrl(), "th_howibuy", "th_howibuy");
	}

	@Override
	public void close() throws InterruptedException {
		if (howiBuy != null) howiBuy.close();
		if (ingestion != null) ingestion.close();
		if (keycloak != null) keycloak.stop();
		if (postgres != null) postgres.stop();
		if (network != null) network.close();
	}
}
