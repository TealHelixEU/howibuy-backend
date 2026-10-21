package eu.tealhelix.catalogue.tests;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.test.quarkus.WithCatalogueDb;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A Feed is stored while it arrives, never held in memory whole, and a transfer cut off midway leaves nothing behind.
 */
@QuarkusTest
@WithCatalogueDb
class FeedTransferTest {
	private static final String LIME_FRESH = "dc73176c-7069-4a4f-a74f-d60c4a5ec1ee";
	/**
	 * A Catalogue of the tests, whose adapter does not exist, so that its runs fail at once instead of reading the
	 * megabytes of random bytes sent here.
	 */
	private static final String CATALOGUE = "lime-fresh-transfer";
	private static final String FEEDS_PATH = "/api/catalogue/v1/catalogues/" + CATALOGUE + "/feeds?type=SNAPSHOT";
	private static final int MEGABYTE = 1024 * 1024;
	private static final Duration WAIT = Duration.ofSeconds(30);

	@Inject
	DataSource dataSource;

	@ConfigProperty(name = "catalogue.feed-store.directory")
	String feedStoreDirectory;

	@TestHTTPResource
	URI baseUri;

	private final Random random = new Random(42);

	@BeforeEach
	void createTheCatalogueOfTheTests() throws SQLException {
		execute("INSERT INTO IN_CATALOGUE (id, retailer_id, country, primary_language, accepted_languages, adapter) VALUES "
				+ "('" + CATALOGUE + "', '" + LIME_FRESH + "', 'GR', 'el', '{el}', 'no-such-adapter')");
	}

	@AfterEach
	void removeTheCatalogueOfTheTests() throws SQLException, InterruptedException {
		assertThat(eventually(() -> countRuns("status IN ('RECEIVED', 'PROCESSING')") == 0)).as("the runs of the tests are processed").isTrue();
		execute("DELETE FROM IN_INGESTION_RUN WHERE catalogue_id = '" + CATALOGUE + "'");
		execute("DELETE FROM IN_CATALOGUE WHERE id = '" + CATALOGUE + "'");
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void theFeedIsStoredWhileItArrives() throws Exception {
		var body = new PipedOutputStream();
		var bodyIn = new PipedInputStream(body, MEGABYTE);
		var request = HttpRequest.newBuilder(baseUri.resolve(FEEDS_PATH))
				.header("Content-Type", "application/octet-stream")
				.POST(HttpRequest.BodyPublishers.ofInputStream(() -> bodyIn))
				.build();
		try (var client = HttpClient.newHttpClient()) {
			var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

			writeRandomBytes(body, 8 * MEGABYTE);
			assertThat(eventually(() -> bytesBeingReceived() > MEGABYTE))
					.as("more than a megabyte of the Feed is stored before the rest of it is sent")
					.isTrue();
			writeRandomBytes(body, 8 * MEGABYTE);
			body.close();

			assertThat(response.get().statusCode()).isEqualTo(202);
		}
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aTransferCutOffMidwayLeavesNoRunAndNoFile() throws Exception {
		var runsBefore = countRuns("TRUE");
		try (var socket = new Socket(baseUri.getHost(), baseUri.getPort())) {
			var out = socket.getOutputStream();
			out.write(("POST " + FEEDS_PATH + " HTTP/1.1\r\n"
					+ "Host: " + baseUri.getHost() + ":" + baseUri.getPort() + "\r\n"
					+ "Content-Type: application/octet-stream\r\n"
					+ "Content-Length: " + 16 * MEGABYTE + "\r\n"
					+ "\r\n").getBytes(US_ASCII));
			writeRandomBytes(out, 4 * MEGABYTE);
			assertThat(eventually(() -> bytesBeingReceived() > 0)).as("the Feed is being received").isTrue();
		}

		assertThat(eventually(() -> bytesBeingReceived() == 0 && filesBeingReceived() == 0))
				.as("the part received is removed")
				.isTrue();
		assertThat(countRuns("TRUE")).isEqualTo(runsBefore);
	}

	private void writeRandomBytes(OutputStream out, int count) throws IOException {
		var chunk = new byte[64 * 1024];
		for (int written = 0; written < count; written += chunk.length) {
			random.nextBytes(chunk);
			out.write(chunk);
		}
		out.flush();
	}

	/**
	 * @param condition A condition on the runs of the Catalogue of the tests, in SQL
	 */
	private long countRuns(String condition) {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
			 var resultSet = statement.executeQuery("SELECT COUNT(*) FROM IN_INGESTION_RUN WHERE catalogue_id = '" + CATALOGUE + "' AND " + condition)) {
			resultSet.next();
			return resultSet.getLong(1);
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	private void execute(String sql) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	/**
	 * The size of the Feeds that are being received; a Feed received whole is moved out of the files counted here.
	 */
	private long bytesBeingReceived() {
		try (Stream<Path> files = filesOfFeedsBeingReceived()) {
			return files.mapToLong(FeedTransferTest::size).sum();
		}
	}

	private long filesBeingReceived() {
		try (Stream<Path> files = filesOfFeedsBeingReceived()) {
			return files.count();
		}
	}

	private Stream<Path> filesOfFeedsBeingReceived() {
		try {
			var directory = Path.of(feedStoreDirectory);
			return Files.exists(directory)
					? Files.list(directory).filter(file -> file.getFileName().toString().startsWith("receiving-"))
					: Stream.empty();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static long size(Path file) {
		try {
			return Files.size(file);
		} catch (IOException e) {
			return 0;
		}
	}

	private static boolean eventually(BooleanSupplier condition) throws InterruptedException {
		var deadline = Instant.now().plus(WAIT);
		while (Instant.now().isBefore(deadline)) {
			if (condition.getAsBoolean()) return true;
			Thread.sleep(50);
		}
		return condition.getAsBoolean();
	}
}
