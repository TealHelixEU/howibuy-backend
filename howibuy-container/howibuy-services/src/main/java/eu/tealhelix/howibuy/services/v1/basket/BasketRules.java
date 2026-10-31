package eu.tealhelix.howibuy.services.v1.basket;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.BasketGrade;
import eu.tealhelix.howibuy.v1.types.ProductGrade;

/**
 * The rules that judge a basket from the Overall Scores of its scored products: the {@link ProductGrade} of each, the
 * {@link BasketGrade}, the XP the basket earns and its Problematic Products.
 * <p>
 * The Basket Grade and XP rules are stubs until the real rules are specified: the Basket Grade is the worst Product
 * Grade, and every product earns XP by its grade alone.
 */
public final class BasketRules {
	/**
	 * The lowest Overall Score of a product graded {@link ProductGrade#A}.
	 */
	static final double GRADE_A_MIN_SCORE = 0.8;
	/**
	 * The lowest Overall Score of a product graded {@link ProductGrade#B}.
	 */
	static final double GRADE_B_MIN_SCORE = 0.6;

	/**
	 * A product of a basket that has an Overall Score under the Weighting Profile in force.
	 */
	public record ScoredBasketProduct(ProductKey productKey, double overallScore) {
	}

	/**
	 * What the rules say about a basket.
	 *
	 * @param productGrades          the grade of every scored product
	 * @param basketGrade            the grade of the basket, {@code null} when it has no scored product
	 * @param xpValue                the XP the basket earns
	 * @param problematicProductKeys the products graded below {@link ProductGrade#A}, lowest Overall Score first
	 */
	public record BasketVerdict(
			Map<ProductKey, ProductGrade> productGrades,
			@Nullable BasketGrade basketGrade,
			int xpValue,
			List<ProductKey> problematicProductKeys) {
	}

	public ProductGrade gradeOf(double overallScore) {
		if (overallScore >= GRADE_A_MIN_SCORE) {
			return ProductGrade.A;
		} else if (overallScore >= GRADE_B_MIN_SCORE) {
			return ProductGrade.B;
		} else {
			return ProductGrade.C;
		}
	}

	/**
	 * Judge a basket of distinct products.
	 */
	public BasketVerdict judge(List<ScoredBasketProduct> products) {
		var productGrades = new LinkedHashMap<ProductKey, ProductGrade>();
		products.forEach(product -> productGrades.put(product.productKey(), gradeOf(product.overallScore())));
		var problematicProductKeys = products.stream()
				.filter(product -> productGrades.get(product.productKey()) != ProductGrade.A)
				.sorted(Comparator.comparingDouble(ScoredBasketProduct::overallScore))
				.map(ScoredBasketProduct::productKey)
				.toList();
		return new BasketVerdict(
				productGrades,
				basketGradeOf(productGrades.values()),
				productGrades.values().stream().mapToInt(BasketRules::xpOf).sum(),
				problematicProductKeys);
	}

	/**
	 * The worst of the grades, {@code null} for none.
	 */
	private static @Nullable BasketGrade basketGradeOf(Collection<ProductGrade> productGrades) {
		return productGrades.stream()
				.max(Comparator.naturalOrder())
				.map(worst -> switch (worst) {
					case A -> BasketGrade.A;
					case B -> BasketGrade.B;
					case C -> BasketGrade.C;
				})
				.orElse(null);
	}

	private static int xpOf(ProductGrade productGrade) {
		return switch (productGrade) {
			case A -> 3;
			case B -> 1;
			case C -> 0;
		};
	}
}
