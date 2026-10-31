package eu.tealhelix.howibuy.services.v1.basket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.catalogue.v1.types.impl.ProductKeyImpl;
import eu.tealhelix.howibuy.services.v1.basket.BasketRules.ScoredBasketProduct;
import eu.tealhelix.howibuy.v1.types.BasketGrade;
import eu.tealhelix.howibuy.v1.types.ProductGrade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class BasketRulesTest {
	private final BasketRules sut = new BasketRules();

	@ParameterizedTest
	@CsvSource({
			"1.0,    A",
			"0.8,    A",
			"0.7999, B",
			"0.6,    B",
			"0.5999, C",
			"0.0,    C",
	})
	void gradesAProductByItsOverallScore(double overallScore, ProductGrade expected) {
		assertEquals(expected, sut.gradeOf(overallScore));
	}

	@ParameterizedTest
	@CsvSource({
			"0.9,          A, 3",
			"0.9 0.8,      A, 6",
			"0.9 0.7,      B, 4",
			"0.7 0.6,      B, 2",
			"0.9 0.7 0.1,  C, 4",
			"0.5,          C, 0",
	})
	void gradesTheBasketByItsWorstProductAndCountsTheXp(String overallScores, BasketGrade basketGrade, int xpValue) {
		var verdict = sut.judge(products(overallScores));

		assertEquals(basketGrade, verdict.basketGrade());
		assertEquals(xpValue, verdict.xpValue());
	}

	@Test
	void reportsTheGradeOfEveryProduct() {
		var verdict = sut.judge(products("0.9 0.7 0.1"));

		assertEquals(Map.of(key(0), ProductGrade.A, key(1), ProductGrade.B, key(2), ProductGrade.C),
				verdict.productGrades());
	}

	@Test
	void reportsTheProductsBelowALowestOverallScoreFirst() {
		var verdict = sut.judge(products("0.7 0.9 0.1 0.65 0.85 0.3"));

		assertEquals(List.of(key(2), key(5), key(3), key(0)), verdict.problematicProductKeys());
	}

	@Test
	void keepsTheOrderOfTheBasketForProblematicProductsOfEqualOverallScore() {
		var verdict = sut.judge(products("0.7 0.5 0.7"));

		assertEquals(List.of(key(1), key(0), key(2)), verdict.problematicProductKeys());
	}

	@Test
	void judgesAnEmptyBasketWithoutAGrade() {
		var verdict = sut.judge(List.of());

		assertNull(verdict.basketGrade());
		assertEquals(0, verdict.xpValue());
		assertEquals(Map.of(), verdict.productGrades());
		assertEquals(List.of(), verdict.problematicProductKeys());
	}

	/**
	 * One product per space-separated Overall Score, keyed by its position.
	 */
	private static List<ScoredBasketProduct> products(String overallScores) {
		var scores = overallScores.trim().split(" +");
		return IntStream.range(0, scores.length)
				.mapToObj(position -> new ScoredBasketProduct(key(position), Double.parseDouble(scores[position])))
				.toList();
	}

	private static ProductKey key(int position) {
		return new ProductKeyImpl("product-" + position);
	}
}
