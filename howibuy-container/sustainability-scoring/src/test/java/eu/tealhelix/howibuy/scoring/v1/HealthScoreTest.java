package eu.tealhelix.howibuy.scoring.v1;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthScoreTest {
	@Test
	void readsEveryNutriScoreGradeOntoAnEvenlySpacedScale() {
		assertThat(HealthScore.forNutriScore("Nutriscore_A")).hasValue(1.0);
		assertThat(HealthScore.forNutriScore("Nutriscore_B")).hasValue(0.75);
		assertThat(HealthScore.forNutriScore("Nutriscore_C")).hasValue(0.5);
		assertThat(HealthScore.forNutriScore("Nutriscore_D")).hasValue(0.25);
		assertThat(HealthScore.forNutriScore("Nutriscore_E")).hasValue(0.0);
	}

	/**
	 * Products outside the Nutri-Score scheme — alcoholic drinks and infant food, in this dataset — are stored with the
	 * label {@code "0"}. They have no health score, and WP3's method gives them no overall score at all rather than
	 * scoring them on their remaining three dimensions.
	 */
	@Test
	void hasNoScoreForAProductOutsideTheNutriScoreScheme() {
		assertThat(HealthScore.forNutriScore("0")).isEmpty();
	}

	@Test
	void hasNoScoreForAnUnrecognisedOrAbsentLabel() {
		assertThat(HealthScore.forNutriScore("Nutriscore_F")).isEmpty();
		assertThat(HealthScore.forNutriScore("")).isEmpty();
		assertThat(HealthScore.forNutriScore(null)).isEmpty();
	}
}
