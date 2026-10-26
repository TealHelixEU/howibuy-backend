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
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import eu.tealhelix.howibuy.services.v1.ClassificationService;
import eu.tealhelix.howibuy.v1.model.ClassificationAttemptInput;
import eu.tealhelix.howibuy.v1.model.ImmutableClassificationAttemptInput;
import eu.tealhelix.howibuy.v1.types.impl.ArchetypeProductIdImpl;
import eu.tealhelix.howibuy.v1.types.impl.RetailerProductIdImpl;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("classification-attempts")
@Tag(name = CLASSIFICATION, description = CLASSIFICATION_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class ClassificationAttemptsResource {
	@Inject
	ClassificationService classificationService;

	@Operation(
			summary = "Record a Classification Attempt",
			description = "Records an attempt made outside this application, by an expert or one of us, and resolves the product's Classification from its attempts, in one transaction. The attempts recorded for one product at the same time resolve one after the other.")
	@APIResponse(responseCode = "204", description = "The attempt was recorded and the Classification resolved.")
	@APIResponse(responseCode = "400", description = "A required field is missing or malformed, the classifier is the LLM, whose attempts this application makes itself, or the product is not named in the language. Nothing was recorded.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = ClassificationsResource.FORBIDDEN)
	@APIResponse(responseCode = "404", description = "There is no such Retailer Product or Archetype Product. Nothing was recorded.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "409", description = "The input hash is no longer the product's, because the product changed while it was being classified. Nothing was recorded.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> recordAttempt(@Context ContainerRequestContext crc, ClassificationAttemptRequest request) {
		var user = currentUser(crc);
		return Uni.createFrom().item(() -> toAttempt(request))
				.flatMap(attempt -> classificationService.recordAttempt(user, new RetailerProductIdImpl(uuid("retailerProductId", request.retailerProductId())), attempt));
	}

	private static ClassificationAttemptInput toAttempt(ClassificationAttemptRequest request) {
		if (request == null) throw RequiredInputMissingException.fromRequiredInputName("body");
		return ImmutableClassificationAttemptInput.builder()
				.inputHash(requiredText("inputHash", request.inputHash()))
				.language(requiredText("language", request.language()))
				.classifier(ClassificationsResource.classifier(requiredText("classifier", request.classifier()), "It must be MANUAL or EXPERT"))
				.classifierVersion(requiredText("classifierVersion", request.classifierVersion()))
				.archetypeProductId(request.archetypeProductId() != null ? new ArchetypeProductIdImpl(uuid("archetypeProductId", request.archetypeProductId())) : null)
				.build();
	}
}
