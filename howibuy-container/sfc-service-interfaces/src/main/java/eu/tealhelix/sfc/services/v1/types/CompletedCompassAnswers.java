package eu.tealhelix.sfc.services.v1.types;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.tealhelix.sfc.v1.types.ScaleOption;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;

/**
 * What a user said mattered to them on one completed compass attempt, with the answers gathered under the dimension
 * each question addresses. Grouped here rather than by the caller, because which question belongs to which dimension
 * is the compass's own business.
 * <p>
 * A completed attempt has every question answered, so each dimension carries one answer per question in it. The
 * {@link #attemptId() attempt id} identifies the attempt the answers came from; a completed attempt is immutable, so
 * anything derived from these answers may be cached against it.
 */
public record CompletedCompassAnswers(UUID attemptId, Map<SustainabilityDimension, List<ScaleOption>> answersByDimension) {
}
