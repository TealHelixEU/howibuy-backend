package eu.tealhelix.howibuy.v1.model;

import java.util.List;

import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.types.Nullable;
import eu.tealhelix.howibuy.v1.types.BasketAssessmentOutcomeType;
import eu.tealhelix.howibuy.v1.types.BasketMatch;
import org.immutables.value.Value;

/**
 * Outcome of the sustainability assessment and consumer labeling process for a basket.
 */
@Value.Immutable
public interface BasketAssessmentOutcome {
	/**
	 * Whether the basket could be judged at all. If the value is {@link BasketAssessmentOutcomeType#ASSESSED}, the
	 * basket has a Basket Match and its label.
	 */
	BasketAssessmentOutcomeType getType();

	/**
	 * How well the basket matches the user's own priorities, judged on the personal Overall Scores of its products:
	 * their average above 0.8 is {@link BasketMatch#EXCELLENT}, above 0.6 {@link BasketMatch#GOOD}, otherwise
	 * {@link BasketMatch#NEEDS_IMPROVEMENT}; {@code null} when there was nothing to assess.
	 */
	@Nullable
	BasketMatch getBasketMatch();

	/**
	 * A user-friendly label for the Basket Match, translated in the requested language, if available;
	 * {@code null} when there was nothing to assess.
	 */
	@Nullable
	String getBasketMatchLabel();

	/**
	 * A potentially empty intervention message in the requested output language of the user.
	 */
	@Nullable
	String getIntervention();

	/**
	 * The product keys of the Problematic Products of the basket, those whose scientific Overall Score is 0.8 or less,
	 * lowest scientific Overall Score first.
	 */
	List<ProductKey> getProblematicProductKeys();

	/**
	 * The user's experience points so far. If this outcome is for a basket that has already been purchased, this number
	 * contains the {@link BasketAssessmentOutcome#getXpValue() value of the basket}.
	 */
	int getTotalXp();

	/**
	 * The number of experience points this basket earns, judged on the scientific Overall Scores of its products: 10 for
	 * every product above 0.8, and a bonus when the basket has at least 5 products with an Overall Score, 150 when their
	 * average is above 0.8, otherwise 50 when it is above 0.6. If already purchased, this number has already been added
	 * to the {@link #getTotalXp() user's total XP}.
	 */
	int getXpValue();
}
