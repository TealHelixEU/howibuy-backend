package eu.tealhelix.howibuy.jaxrs.v1.classification;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.JaxRsUtils.currentUser;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.CLASSIFICATION;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.CLASSIFICATION_DESC;
import static eu.tealhelix.howibuy.jaxrs.v1.RequestValuesHelper.required;
import static eu.tealhelix.howibuy.jaxrs.v1.RequestValuesHelper.requiredText;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import eu.tealhelix.howibuy.services.v1.ClassificationService;
import eu.tealhelix.howibuy.v1.model.ClassificationAttempt;
import eu.tealhelix.howibuy.v1.model.ClassificationToReview;
import eu.tealhelix.howibuy.v1.model.PendingProduct;
import eu.tealhelix.howibuy.v1.types.ArchetypeProductId;
import eu.tealhelix.howibuy.v1.types.Classifier;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("classifications")
@Tag(name = CLASSIFICATION, description = CLASSIFICATION_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class ClassificationsResource {
	static final String FORBIDDEN = "The caller is neither the Catalogue Sync nor an operator.";

	@Inject
	ClassificationService classificationService;

	@Operation(
			summary = "Read the products waiting to be classified",
			description = "The products without a Classification, and those changed since theirs was resolved, the one changed longest ago first. An attempt at one is recorded with the input hash read here.")
	@APIResponse(responseCode = "200", description = "The pending products, as many as the limit at most.")
	@APIResponse(responseCode = "400", description = "The limit is missing or out of its range.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = FORBIDDEN)
	@GET
	@Path("pending")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<PendingProductDto>> findPending(
			@Context ContainerRequestContext crc,
			@Parameter(description = "How many products to read at most, from 1 to " + ClassificationService.MAX_PER_PAGE + ".", required = true) @QueryParam("limit") Integer limit
	) {
		var user = currentUser(crc);
		return Uni.createFrom().item(() -> required("limit", limit))
				.flatMap(checkedLimit -> classificationService.findPending(user, checkedLimit))
				.map(products -> products.stream().map(ClassificationsResource::toDto).toList());
	}

	@Operation(
			summary = "Read the Classifications to review",
			description = "The products whose current Classification was resolved from attempts that disagree, the one resolved longest ago first, with their names and the attempts. A manual attempt settles a review. A product changed since its Classification was resolved is pending instead.")
	@APIResponse(responseCode = "200", description = "The Classifications to review, as many as the limit at most.")
	@APIResponse(responseCode = "400", description = "The limit is missing or out of its range.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = FORBIDDEN)
	@GET
	@Path("review")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<ClassificationToReviewDto>> findToReview(
			@Context ContainerRequestContext crc,
			@Parameter(description = "How many products to read at most, from 1 to " + ClassificationService.MAX_PER_PAGE + ".", required = true) @QueryParam("limit") Integer limit
	) {
		var user = currentUser(crc);
		return Uni.createFrom().item(() -> required("limit", limit))
				.flatMap(checkedLimit -> classificationService.findToReview(user, checkedLimit))
				.map(products -> products.stream().map(ClassificationsResource::toDto).toList());
	}

	@Operation(
			summary = "Reopen the Classifications that came from a classifier version",
			description = "Makes the products whose Classification came from the version pending again, after the classifier improved, so it classifies them again. A Classification came from the versions of the latest attempts, one per language, of the classifier that decided it. A reopened Classification keeps its archetype until an attempt resolves it again.")
	@APIResponse(responseCode = "200", description = "The Classifications were reopened; the answer tells how many.")
	@APIResponse(responseCode = "400", description = "A required field is missing or malformed.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = "The caller is not an operator.")
	@POST
	@Path("reopening")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<ClassificationReopeningResponse> reopen(@Context ContainerRequestContext crc, ClassificationReopeningRequest request) {
		var user = currentUser(crc);
		return Uni.createFrom().item(() -> required("body", request))
				.flatMap(body -> classificationService.reopen(
						user,
						classifier(requiredText("classifier", body.classifier()), "It must be MANUAL, EXPERT or LLM"),
						requiredText("classifierVersion", body.classifierVersion())
				))
				.map(ClassificationReopeningResponse::new);
	}

	static Classifier classifier(String name, String hint) {
		try {
			return Classifier.valueOf(name);
		} catch (IllegalArgumentException e) {
			throw BadInputValueException.fromInputNameAndHint("classifier", hint);
		}
	}

	private static PendingProductDto toDto(PendingProduct product) {
		return new PendingProductDto(product.getId().asString(), product.getInputHash(), product.getLanguages());
	}

	private static ClassificationToReviewDto toDto(ClassificationToReview product) {
		var classification = product.getClassification();
		return new ClassificationToReviewDto(
				product.getId().asString(),
				product.getNames(),
				classification.getInputHash(),
				asString(classification.getArchetypeProductId()),
				classification.getResolvedAt(),
				product.getAttempts().stream().map(ClassificationsResource::toDto).toList()
		);
	}

	static ClassificationAttemptDto toDto(ClassificationAttempt attempt) {
		return new ClassificationAttemptDto(attempt.getLanguage(), attempt.getClassifier().name(), attempt.getClassifierVersion(), asString(attempt.getArchetypeProductId()), attempt.getCreatedAt(), attempt.getDiagnostics());
	}

	static String asString(ArchetypeProductId id) {
		return id != null ? id.asString() : null;
	}
}
