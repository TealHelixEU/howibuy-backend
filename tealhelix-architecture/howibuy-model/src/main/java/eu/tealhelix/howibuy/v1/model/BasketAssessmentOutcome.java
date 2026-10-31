package eu.tealhelix.howibuy.v1.model;

import java.util.List;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.BasketAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.BasketGrade;
import org.immutables.value.Value;

/**
 * Outcome of the sustainability assessment and consumer labeling process for a basket.
 */
@Value.Immutable
public interface BasketAssessmentOutcome {
	/**
	 * Whether the basket could be judged at all. If the value is {@link BasketAssessmentOutcomeType#ASSESSED}, the
	 * basket has a grade and its label.
	 */
	BasketAssessmentOutcomeType getType();

	/**
	 * The sustainability grade of a basket, {@code null} when there was nothing to assess.
	 */
	@Nullable
	BasketGrade getBasketGrade();

	/**
	 * A user-friendly label for the basket grade, translated in the requested language, if available;
	 * {@code null} when there was nothing to assess.
	 */
	@Nullable
	String getBasketGradeLabel();

	/**
	 * A potentially empty intervention message in the requested output language of the user.
	 */
	@Nullable
	String getIntervention();

	/**
	 * The product keys of the products in the basket that have sustainability issues, most problematic first.
	 */
	List<ProductKey> getProblematicProductKeys();

	/**
	 * The user's experience points so far. If this outcome is for a basket that has already been purchased, this number
	 * contains the {@link BasketAssessmentOutcome#getXpValue() value of the basket}.
	 */
	int getTotalXp();

	/**
	 * The number of experience points this basket earns. If already purchased, this number has already been added to
	 * the {@link #getTotalXp() user's total XP}.
	 */
	int getXpValue();
}
