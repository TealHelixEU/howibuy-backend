package eu.tealhelix.howibuy.jaxrs.v1.classification;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.JaxRsUtils.currentUser;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.CLASSIFICATION;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.CLASSIFICATION_DESC;
import static eu.tealhelix.howibuy.jaxrs.v1.RequestValuesHelper.requiredText;
import static eu.tealhelix.howibuy.jaxrs.v1.RequestValuesHelper.uuid;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import eu.tealhelix.howibuy.services.v1.ClassificationService;
import eu.tealhelix.howibuy.services.v1.types.LlmClassification;
import eu.tealhelix.howibuy.v1.model.Classification;
import eu.tealhelix.howibuy.v1.types.impl.RetailerProductIdImpl;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("llm-classifications")
@Tag(name = CLASSIFICATION, description = CLASSIFICATION_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class LlmClassificationsResource {
	@Inject
	ClassificationService classificationService;

	@Operation(
			summary = "Classify a Retailer Product with the LLM",
			description = "The LLM matches the product to an Archetype Product from its name in one language, its characteristics and its tags. Its attempt is recorded, also when it matches none, and resolves the product's Classification from its attempts. A malformed reply of the LLM is answered and nothing is recorded, so the product stays pending. The product is not locked while the LLM works, which takes seconds.")
	@APIResponse(responseCode = "200", description = "The LLM classified the product; the outcome tells how.")
	@APIResponse(responseCode = "400", description = "A required field is missing or malformed, or the product is not named in the language. Nothing was recorded.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = ClassificationsResource.FORBIDDEN)
	@APIResponse(responseCode = "404", description = "There is no such Retailer Product. Nothing was recorded.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "409", description = "The product changed while the LLM was classifying it. Nothing was recorded.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<LlmClassificationResponse> classify(@Context ContainerRequestContext crc, LlmClassificationRequest request) {
		var user = currentUser(crc);
		return Uni.createFrom().item(() -> requireBody(request))
				.flatMap(body -> classificationService.classifyWithLlm(user, new RetailerProductIdImpl(uuid("retailerProductId", body.retailerProductId())), requiredText("language", body.language())))
				.map(LlmClassificationsResource::toResponse);
	}

	private static LlmClassificationRequest requireBody(LlmClassificationRequest request) {
		if (request == null) throw RequiredInputMissingException.fromRequiredInputName("body");
		return request;
	}

	private static LlmClassificationResponse toResponse(LlmClassification classification) {
		return switch (classification) {
			case LlmClassification.Recorded recorded -> new LlmClassificationResponse(
					recorded.attempt().getArchetypeProductId() != null ? LlmClassificationResponse.Outcome.MATCHED : LlmClassificationResponse.Outcome.NO_MATCH,
					ClassificationsResource.toDto(recorded.attempt()),
					toDto(recorded.classification()),
					recorded.attempt().getDiagnostics()
			);
			case LlmClassification.Malformed malformed -> new LlmClassificationResponse(LlmClassificationResponse.Outcome.MALFORMED_REPLY, null, null, malformed.diagnostics());
		};
	}

	private static ClassificationDto toDto(Classification classification) {
		return new ClassificationDto(
				ClassificationsResource.asString(classification.getArchetypeProductId()),
				classification.getInputHash(),
				classification.isNeedsReview(),
				classification.getResolvedAt()
		);
	}
}
