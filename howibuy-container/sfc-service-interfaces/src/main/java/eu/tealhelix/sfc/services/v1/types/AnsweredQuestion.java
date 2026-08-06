package eu.tealhelix.sfc.services.v1.types;

import java.util.Optional;

import eu.tealhelix.sfc.v1.model.Question;
import eu.tealhelix.sfc.v1.types.ScaleOption;

/**
 * A compass question paired with the user's answer on their current attempt, or {@link Optional#empty() empty} when they
 * have not answered it yet. Carried by the review reads so a user can see a category — or the whole compass — alongside
 * the choices they have made.
 */
public record AnsweredQuestion(Question question, Optional<ScaleOption> answer) {
}
