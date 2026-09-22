package eu.tealhelix.common.web.exceptionmap;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A simple response that contains only a single message.
 */
@Schema(description = "An error body carrying nothing but a message saying what was wrong.")
public record SingleMessageResponse(
		@Schema(description = "What was wrong with the request.")
		String message
) {
}
