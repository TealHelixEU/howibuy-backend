package eu.tealhelix.sfc.v1.types;

import java.util.UUID;

import eu.tealhelix.common.types.RepresentableAsString;

public interface QuestionId extends HasQuestionId, RepresentableAsString {
	@Override
	default QuestionId getId() {
		return this;
	}

	UUID asUuid();
}
