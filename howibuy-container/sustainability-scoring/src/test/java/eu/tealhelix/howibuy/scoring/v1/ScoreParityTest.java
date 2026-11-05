package eu.tealhelix.howibuy.scoring.v1;

import static eu.tealhelix.howibuy.scoring.v1.ProductIndicatorsTestUtils.idOf;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import eu.tealhelix.howibuy.v1.types.ScoredSustainabilityDimension;
import eu.tealhelix.howibuy.v1.types.SustainabilityIndicator;
import org.junit.jupiter.api.Test;

/**
 * The engine's scores of every product of WP3's 10-27 database against the scores WP3's notebook gives them,
 * {@code paper_analysis_2026_07_31_v15.qmd} under its normalisation profile {@code log_tails}, as it wrote them to
 * {@code CORE_scores_by_profile.csv}. WP3 computed them with all 2,451 products in the scale, the 47 without a
 * Nutri-Score included, so they are compared with {@link ScoredCorpus#scoreAll}.
 * <p>
 * The fixture is written by {@code tealhelix-architecture/src/scripts/generate-scoring-parity-fixture.js} from the two
 * WP3 files in {@code tealhelix-architecture/src/data/wp3/}.
 */
class ScoreParityTest {
	private static final String FIXTURE = "/log-tails-parity-v2026-10-27.csv";
	private static final double TOLERANCE = 1e-9;
	private static final int PRODUCTS = 2451;
	private static final int PRODUCTS_WITHOUT_A_NUTRI_SCORE = 47;
	private static final String NO_NUTRI_SCORE = "0";

	private static final List<Wp3Product> WP3_PRODUCTS = readFixture();

	@Test
	void scoresEveryProductAsWp3Does() {
		var corpus = ScoredCorpus.scoreAll(products());

		var mismatches = new ArrayList<String>();
		for (var wp3Product : WP3_PRODUCTS) {
			var scored = corpus.require(wp3Product.indicators().productId());
			wp3Product.scores().forEach((dimension, expected) -> {
				var actual = scored.normalisedScores().get(dimension);
				if (Math.abs(actual - expected) > TOLERANCE) mismatches.add(mismatch(wp3Product, dimension.name(), expected, actual));
			});
			var overall = scored.overallScore(ScientificWeights.profile());
			if (Math.abs(overall - wp3Product.overallScore()) > TOLERANCE) mismatches.add(mismatch(wp3Product, "overall", wp3Product.overallScore(), overall));
		}

		assertThat(corpus.getProducts()).hasSize(PRODUCTS);
		assertThat(mismatches).as("%d scores differ from WP3's by more than %s; the first: %s", mismatches.size(), TOLERANCE,
				mismatches.stream().limit(10).toList()).isEmpty();
	}

	@Test
	void gatesEveryProductAsWp3Does() {
		var corpus = ScoredCorpus.scoreAll(products());

		List<String> gatedDifferently = WP3_PRODUCTS.stream()
				.filter(wp3Product -> isGoodEnough(corpus.require(wp3Product.indicators().productId()).overallScore(ScientificWeights.profile()))
						!= isGoodEnough(wp3Product.overallScore()))
				.map(wp3Product -> wp3Product.indicators().agbCode())
				.toList();

		assertThat(gatedDifferently).as("good enough on one side of the parity and not on the other").isEmpty();
	}

	/**
	 * The production entry point leaves the products without a Nutri-Score out of the scale, as WP3 decided after
	 * computing the scores this test compares with. Should that exclusion disappear, the two entry points would score
	 * alike, and this test fails.
	 */
	@Test
	void scoresTheCorpusOnAScaleWithoutTheProductsThatHaveNoNutriScore() {
		List<ProductIndicators> products = products();
		var corpus = ScoredCorpus.score(products);
		var everyProduct = ScoredCorpus.scoreAll(products);

		assertThat(products.stream().filter(product -> NO_NUTRI_SCORE.equals(product.nutriScore()))).hasSize(PRODUCTS_WITHOUT_A_NUTRI_SCORE);
		assertThat(corpus.getProducts()).hasSize(PRODUCTS - PRODUCTS_WITHOUT_A_NUTRI_SCORE);
		var largestDifference = corpus.getProducts().stream()
				.mapToDouble(scored -> Math.abs(scored.overallScore(ScientificWeights.profile())
						- everyProduct.require(scored.productId()).overallScore(ScientificWeights.profile())))
				.max().orElseThrow();
		assertThat(largestDifference).as("the largest difference in a scientific Overall Score between the two scales").isGreaterThan(1e-3);
	}

	private static boolean isGoodEnough(double scientificOverallScore) {
		return scientificOverallScore >= SubstitutionSearch.GATE;
	}

	private static List<ProductIndicators> products() {
		return WP3_PRODUCTS.stream().map(Wp3Product::indicators).toList();
	}

	private static String mismatch(Wp3Product wp3Product, String score, double expected, double actual) {
		return wp3Product.indicators().agbCode() + " " + score + ": WP3 " + expected + ", engine " + actual;
	}

	/**
	 * A product of the fixture: what the engine scores it from, and the scores WP3 gives it.
	 */
	private record Wp3Product(ProductIndicators indicators, Map<ScoredSustainabilityDimension, Double> scores, double overallScore) {
	}

	private static List<Wp3Product> readFixture() {
		try (var in = Objects.requireNonNull(ScoreParityTest.class.getResourceAsStream(FIXTURE), FIXTURE);
				var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			var header = List.of(reader.readLine().split(","));
			return reader.lines().map(line -> toWp3Product(header, line.split(","))).toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Wp3Product toWp3Product(List<String> header, String[] fields) {
		assertThat(fields).as("a row of the fixture").hasSize(header.size());
		var agbCode = fields[header.indexOf("agb_code")];

		var values = new EnumMap<SustainabilityIndicator, Double>(SustainabilityIndicator.class);
		Arrays.stream(SustainabilityIndicator.values())
				.forEach(indicator -> values.put(indicator, Double.parseDouble(fields[header.indexOf(indicator.name())])));
		var scores = new EnumMap<ScoredSustainabilityDimension, Double>(ScoredSustainabilityDimension.class);
		Arrays.stream(ScoredSustainabilityDimension.values())
				.forEach(dimension -> scores.put(dimension, Double.parseDouble(fields[header.indexOf(dimension.name())])));

		var indicators = new ProductIndicators(
				idOf(agbCode), ProductIndicatorsTestUtils.SOME_CATEGORY, agbCode, values,
				fields[header.indexOf("nutri_score")], Integer.parseInt(fields[header.indexOf("nutri_score_points")]));
		return new Wp3Product(indicators, scores, Double.parseDouble(fields[header.indexOf("OVERALL")]));
	}
}
