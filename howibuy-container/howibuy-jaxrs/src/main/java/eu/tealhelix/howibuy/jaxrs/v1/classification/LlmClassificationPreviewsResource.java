package eu.tealhelix.howibuy.jaxrs.v1.classification;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.JaxRsUtils.currentUser;
import static eu.tealhelix.common.web.RequestValuesHelper.required;
import static eu.tealhelix.common.web.RequestValuesHelper.requiredText;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.CLASSIFICATION;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.CLASSIFICATION_DESC;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import eu.tealhelix.howibuy.services.v1.ClassificationService;
import eu.tealhelix.howibuy.services.v1.types.ArchetypeMatchOutcome;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("llm-classification-previews")
@Tag(name = CLASSIFICATION, description = CLASSIFICATION_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class LlmClassificationPreviewsResource {
	@Inject
	ClassificationService classificationService;

	@Operation(
			summary = "Preview how the LLM classifies product data",
			description = "The LLM matches the product data to an Archetype Product as it matches a Retailer Product when classifying it, from the same text: trimmed, with its Unicode composed. Nothing is stored, so posted product data can be tried and the matching evaluated.")
	@APIResponse(responseCode = "200", description = "The LLM matched the product data; the outcome tells how.")
	@APIResponse(responseCode = "400", description = "A required field is missing, blank or malformed.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = "The caller is not an operator.")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<LlmClassificationPreviewResponse> preview(@Context ContainerRequestContext crc, LlmClassificationPreviewRequest request) {
		var user = currentUser(crc);
		return Uni.createFrom().item(() -> required("body", request))
				.flatMap(body -> classificationService.previewWithLlm(
						user,
						requiredText("language", body.language()),
						requiredText("name", body.name()),
						characteristics(body.characteristics()),
						tags(body.tags())
				))
				.map(LlmClassificationPreviewsResource::toResponse);
	}

	private static Map<String, String> characteristics(Map<String, String> characteristics) {
		if (characteristics == null) return Map.of();
		if (characteristics.values().stream().anyMatch(Objects::isNull)) {
			throw BadInputValueException.fromInputNameAndHint("characteristics", "Their values must be text");
		}
		return characteristics;
	}

	private static List<String> tags(List<String> tags) {
		if (tags == null) return List.of();
		if (tags.stream().anyMatch(Objects::isNull)) {
			throw BadInputValueException.fromInputNameAndHint("tags", "They must be text");
		}
		return tags;
	}

	private static LlmClassificationPreviewResponse toResponse(ArchetypeMatchOutcome outcome) {
		return switch (outcome) {
			case ArchetypeMatchOutcome.Matched matched -> new LlmClassificationPreviewResponse(LlmClassificationOutcome.MATCHED, ClassificationsResource.asString(matched.archetypeProductId()), matched.diagnostics());
			case ArchetypeMatchOutcome.NoMatch noMatch -> new LlmClassificationPreviewResponse(LlmClassificationOutcome.NO_MATCH, null, noMatch.diagnostics());
			case ArchetypeMatchOutcome.Malformed malformed -> new LlmClassificationPreviewResponse(LlmClassificationOutcome.MALFORMED_REPLY, null, malformed.diagnostics());
		};
	}
}
