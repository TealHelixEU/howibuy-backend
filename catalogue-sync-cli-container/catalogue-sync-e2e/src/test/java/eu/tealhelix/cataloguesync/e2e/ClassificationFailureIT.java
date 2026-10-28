package eu.tealhelix.cataloguesync.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * An attempt of the {@code classify} command fails when HowiBuy's LLM does, and the command goes on with the others. It
 * runs the pipeline of {@link ClassificationIT}, but the fake standing in for Ollama fails its first chat and the one
 * retry that langchain4j makes of a failed chat. The attempts are made one after the other, so both belong to the first
 * attempt alone, and every other attempt matches.
 */
class ClassificationFailureIT {
	/** A chat and the one retry langchain4j makes of it. */
	private static final int FAILING_CHATS = 2;

	private static final Pipeline pipeline = new Pipeline("classification-failure");
	private static FakeOllama ollama;

	@BeforeAll
	static void startThePipeline() throws IOException, InterruptedException {
		ollama = new FakeOllama("1", FAILING_CHATS);
		pipeline.start(Map.of("quarkus.langchain4j.ollama.productAssessment.base-url", ollama.url()));
	}

	@AfterAll
	static void stopThePipeline() throws InterruptedException {
		pipeline.close();
		if (ollama != null) ollama.close();
	}

	@Test
	void theClassifyCommandReportsTheFailedAttemptAndGoesOnWithTheOthers() throws Exception {
		assertThat(pipeline.pushSnapshot("lime-fresh-gr-snapshot-1.jsonl")).isEqualTo("DONE");
		pipeline.runCatalogueSync("sync", Map.of(), "sync");

		var run = pipeline.runCatalogueSyncToExit("classify", Map.of(), "classify");

		assertThat(run.exitCode()).as("The exit code of the Catalogue Sync, which printed:%n%s", run.output()).isEqualTo(3);
		assertThat(run.output())
				.containsOnlyOnce("Failed, retailerProductId: ")
				.contains("error: HTTP status 500")
				.contains("The classification ended, products: 11, attempts: 22, matched: 21, no match: 0, malformed replies: 0, failures: 1, elapsed: ");
		assertThat(attempts()).isEqualTo(21);
		assertThat(pipeline.howiBuyLog())
				.containsOnlyOnce("A retriable exception occurred. Remaining retries: 1 of 1")
				.containsOnlyOnce("HTTP Request to /api/howibuy/v1/llm-classifications failed");
	}

	/**
	 * @return How many Classification Attempts HowiBuy recorded for the products listed in the Catalogue
	 */
	private static int attempts() throws SQLException {
		var sql = """
				SELECT count(*)
				FROM TH_LISTING l
				JOIN TH_CLASSIFICATION_ATTEMPT a ON a.retailer_product_id = l.retailer_product_id
				WHERE l.catalogue_id = ?""";
		try (var connection = pipeline.howiBuyConnection(); var statement = connection.prepareStatement(sql)) {
			statement.setString(1, Pipeline.CATALOGUE_ID);
			try (var resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getInt(1);
			}
		}
	}
}
