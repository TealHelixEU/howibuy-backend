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
 * The Catalogue Sync carries the products of a Retailer into HowiBuy, and has HowiBuy's LLM classify them. It runs the
 * pipeline of {@link CatalogueSyncIT}, but a fake stands in for Ollama, so this is an integration test, not an
 * end-to-end one. The fake picks the first candidate at every level of the descent, so every attempt matches.
 */
class ClassificationIT {
	/** Small enough that the pending products span several pages. */
	private static final int PAGE_SIZE = 4;

	private static final Pipeline pipeline = new Pipeline("classification");
	private static FakeOllama ollama;

	@BeforeAll
	static void startThePipeline() throws IOException, InterruptedException {
		ollama = new FakeOllama("1");
		pipeline.start(Map.of("quarkus.langchain4j.ollama.productAssessment.base-url", ollama.url()));
	}

	@AfterAll
	static void stopThePipeline() throws InterruptedException {
		pipeline.close();
		if (ollama != null) ollama.close();
	}

	@Test
	void theClassifyCommandHasEveryProductOfTheCatalogueClassified() throws Exception {
		assertThat(pipeline.pushSnapshot("lime-fresh-gr-snapshot-1.jsonl")).isEqualTo("DONE");
		pipeline.runCatalogueSync("sync", Map.of(), "sync");

		var output = pipeline.runCatalogueSync("classify", Map.of("catalogue-sync.classification.page-size", String.valueOf(PAGE_SIZE)), "classify");

		assertThat(output).contains("The classification ended, products: 11, attempts: 22, matched: 22, no match: 0, malformed replies: 0, failures: 0, elapsed: ");
		assertThat(ollama.chats()).isPositive();
		assertThat(classifications()).containsExactlyInAnyOrder(
				new Classification("e2e-bread", "LLM", true),
				new Classification("e2e-chicken", "LLM", true),
				new Classification("e2e-eggs", "LLM", true),
				new Classification("e2e-feta", "LLM", true),
				new Classification("e2e-honey", "LLM", true),
				new Classification("e2e-lentils", "LLM", true),
				new Classification("e2e-milk", "LLM", true),
				new Classification("e2e-olive-oil", "LLM", true),
				new Classification("e2e-oranges", "LLM", true),
				new Classification("e2e-tomatoes", "LLM", true),
				new Classification("e2e-yoghurt", "LLM", true)
		);
	}

	/**
	 * @return The Classifications of the products listed in the Catalogue
	 */
	private static List<Classification> classifications() throws SQLException {
		var sql = """
				SELECT p.product_key, c.classifier, c.archetype_product_id IS NOT NULL
				FROM TH_LISTING l
				JOIN TH_RETAILER_PRODUCT p ON p.id = l.retailer_product_id
				JOIN TH_CLASSIFICATION c ON c.retailer_product_id = p.id
				WHERE l.catalogue_id = ?""";
		try (var connection = pipeline.howiBuyConnection(); var statement = connection.prepareStatement(sql)) {
			statement.setString(1, Pipeline.CATALOGUE_ID);
			try (var resultSet = statement.executeQuery()) {
				var classifications = new ArrayList<Classification>();
				while (resultSet.next()) {
					classifications.add(new Classification(resultSet.getString(1), resultSet.getString(2), resultSet.getBoolean(3)));
				}
				return classifications;
			}
		}
	}

	/**
	 * The Classification of a product in HowiBuy.
	 *
	 * @param matched Whether it matches an archetype
	 */
	private record Classification(String productKey, String classifier, boolean matched) {
	}
}
