package eu.tealhelix.sfc.v1.model;

import eu.tealhelix.sfc.v1.types.CategoryId;
import eu.tealhelix.sfc.v1.types.QuestionId;

import org.immutables.value.Value;

/**
 * A compass question, with its prompt resolved for a single requested language. Questions are ordered within their
 * {@link #getCategoryId() category} by {@link #getPosition() position}; the position is unique per category and is the
 * order in which the user is guided through the category.
 */
@Value.Immutable
public interface Question {
	QuestionId getId();

	CategoryId getCategoryId();

	short getPosition();

	/**
	 * The question prompt, in the requested language.
	 */
	String getText();
}
