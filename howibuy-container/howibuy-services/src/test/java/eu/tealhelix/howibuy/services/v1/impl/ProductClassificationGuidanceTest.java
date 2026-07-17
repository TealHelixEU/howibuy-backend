package eu.tealhelix.howibuy.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import eu.tealhelix.howibuy.services.v1.impl.ProductClassificationGuidance.Rule;
import org.junit.jupiter.api.Test;

class ProductClassificationGuidanceTest {

	@Test
	void returnsNoGuidanceWhenNoRuleIsConfigured() {
		var guidance = new ProductClassificationGuidance(List.of());
		assertEquals("", guidance.forCategoryPath("el", List.of("Meat", "Livestock meat", "Beef")));
	}

	@Test
	void appliesARuleToEveryPathStartingWithItsPrefix() {
		var guidance = new ProductClassificationGuidance(List.of(new Rule("el", List.of("Meat"), "meat guidance")));
		assertEquals("meat guidance", guidance.forCategoryPath("el", List.of("Meat", "Livestock meat", "Beef")));
		assertEquals("meat guidance", guidance.forCategoryPath("el", List.of("Meat", "Poultry", "Chicken")));
	}

	@Test
	void appliesNoGuidanceToAPathThatDoesNotStartWithAnyPrefix() {
		var guidance = new ProductClassificationGuidance(List.of(new Rule("el", List.of("Meat"), "meat guidance")));
		assertEquals("", guidance.forCategoryPath("el", List.of("Milk and dairy products", "Cheese", "Graviera")));
	}

	@Test
	void picksTheMostSpecificRuleWhenSeveralApply() {
		var guidance = new ProductClassificationGuidance(List.of(
				new Rule("el", List.of("Meat"), "meat guidance"),
				new Rule("el", List.of("Meat", "Sausages"), "sausage guidance")));
		assertEquals("sausage guidance", guidance.forCategoryPath("el", List.of("Meat", "Sausages", "Frankfurter")));
		assertEquals("meat guidance", guidance.forCategoryPath("el", List.of("Meat", "Livestock meat", "Beef")));
	}

	@Test
	void doesNotApplyARuleWhosePrefixIsLongerThanThePath() {
		var guidance = new ProductClassificationGuidance(List.of(new Rule("el", List.of("Meat", "Sausages"), "sausage guidance")));
		assertEquals("", guidance.forCategoryPath("el", List.of("Meat")));
	}

	@Test
	void appliesNoGuidanceToAnEmptyPath() {
		var guidance = new ProductClassificationGuidance(List.of(new Rule("el", List.of("Meat"), "meat guidance")));
		assertEquals("", guidance.forCategoryPath("el", List.of()));
	}

	@Test
	void doesNotApplyARuleForADifferentLanguage() {
		var guidance = new ProductClassificationGuidance(List.of(new Rule("el", List.of("Meat"), "meat guidance")));
		assertEquals("meat guidance", guidance.forCategoryPath("el", List.of("Meat", "Livestock meat", "Beef")));
		assertEquals("", guidance.forCategoryPath("lt", List.of("Meat", "Livestock meat", "Beef")));
	}

	private static final String MEAT_L1 = "Meat and meat products (including edible offal)";
	private static final String FISH_L1 = "Fish and other seafood (including amphibians, reptiles, snails and insects)";

	@Test
	void steersPreparationForMeatProducts() {
		var guidance = new ProductClassificationGuidance();
		String forBeef = guidance.forCategoryPath("el", List.of(MEAT_L1, "Livestock meat", "Beef"));
		assertTrue(forBeef.contains("raw"), "meat leaf prompt is steered on preparation, was: " + forBeef);
	}

	@Test
	void steersPreparationForFishProducts() {
		var guidance = new ProductClassificationGuidance();
		String forFish = guidance.forCategoryPath("el", List.of(FISH_L1, "Fish meat", "Sea bream"));
		assertTrue(forFish.contains("raw"), "fish leaf prompt is steered on preparation, was: " + forFish);
	}

	@Test
	void steersMeatAndFishWithTheSameGuidance() {
		var guidance = new ProductClassificationGuidance();
		assertEquals(
				guidance.forCategoryPath("el", List.of(MEAT_L1, "Livestock meat", "Beef")),
				guidance.forCategoryPath("el", List.of(FISH_L1, "Fish meat", "Sea bream")));
	}

	@Test
	void doesNotSteerProductsOutsideMeatAndFish() {
		var guidance = new ProductClassificationGuidance();
		assertEquals("", guidance.forCategoryPath("el", List.of("Milk and dairy products", "Cheese", "Cheese, Manchego")));
	}

	@Test
	void doesNotSteerProcessedMeatWhichIsNotAPlainCut() {
		var guidance = new ProductClassificationGuidance();
		assertEquals("", guidance.forCategoryPath("el", List.of(MEAT_L1, "Sausages", "Toulouse sausage, raw")));
		assertEquals("", guidance.forCategoryPath("el", List.of(MEAT_L1, "Preserved meat", "Bacon, back")));
	}

	@Test
	void doesNotSteerProcessedFishProducts() {
		var guidance = new ProductClassificationGuidance();
		assertEquals("", guidance.forCategoryPath("el", List.of(FISH_L1, "Fish products", "Smoked salmon")));
	}

	@Test
	void steersCutCategoriesBeyondLivestockMeat() {
		var guidance = new ProductClassificationGuidance();
		assertTrue(guidance.forCategoryPath("el", List.of(MEAT_L1, "Poultry", "Chicken")).contains("raw"),
				"poultry is a plain cut and is steered on preparation");
		assertTrue(guidance.forCategoryPath("el", List.of(FISH_L1, "Crustaceans", "Shrimp")).contains("raw"),
				"crustaceans are steered on preparation");
	}

	@Test
	void steersGreekCutsButNotTheSameCutInAnotherLanguage() {
		var guidance = new ProductClassificationGuidance();
		assertTrue(guidance.forCategoryPath("el", List.of(MEAT_L1, "Livestock meat", "Beef")).contains("raw"),
				"Greek cuts are steered on preparation");
		assertEquals("", guidance.forCategoryPath("lt", List.of(MEAT_L1, "Livestock meat", "Beef")),
				"the Greek preparation vocabulary must not be injected into another language");
	}
}
