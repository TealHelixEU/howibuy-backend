package eu.tealhelix.sfc.jaxrs.v1;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.JaxRsUtils.currentUser;
import static eu.tealhelix.sfc.jaxrs.v1.SfcOpenApiTagNames.SFC;
import static eu.tealhelix.sfc.jaxrs.v1.SfcOpenApiTagNames.SFC_DESC;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import eu.tealhelix.sfc.services.v1.CompassReadService;
import eu.tealhelix.sfc.services.v1.types.AnsweredQuestion;
import eu.tealhelix.sfc.v1.types.CategoryId;
import eu.tealhelix.sfc.v1.types.impl.CategoryIdImpl;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Reads of the Sustainable Food Compass for the authenticated end-user, localized via an optional {@code ?lang} query
 * parameter (defaulting to the configured default language): its categories and questions, each question paired with
 * the user's current answer, and the "next question" that guides them forward through a category — with its mirror, the
 * "previous question" they step back to. All reads require an authenticated end-user.
 */
@Path("sfc")
@Tag(name = SFC, description = SFC_DESC)
@SecurityRequirement(name = BEARER_AUTH)
@APIResponse(responseCode = "400", description = "The requested language is not one of the supported ones. The compass is refused rather than served half-translated.",
		content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
@APIResponse(responseCode = "403", description = "A service account presented the token: the compass belongs to an end-user.")
public class CompassResource {
	private static final String LANG_DESCRIPTION = "The language to localize the texts in, as a language tag (`en`, `el`, ...). Left out or blank, the configured default language is used.";
	private static final String CATEGORY_ID_DESCRIPTION = "The category meant, as its id reads in the categories.";

	@Inject
	CompassReadService compassReadService;

	@Operation(
			summary = "Where the user stands in the compass",
			description = "One snapshot of the whole compass: overall progress and the estimated time still to spend, the same per category, the localized labels of the answer scale, and the state of the current attempt — including, once one has been completed, the moment its stability window ends and a fresh attempt may be started.")
	@APIResponse(responseCode = "200", description = "Progress, estimates, scale labels and attempt state, localized.")
	@GET
	@Path("overview")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<CompassOverviewDto> overview(@Context ContainerRequestContext crc, @Parameter(description = LANG_DESCRIPTION) @QueryParam("lang") String lang) {
		return compassReadService.retrieveOverview(currentUser(crc), lang).map(CompassOverviewDto::from);
	}

	@Operation(
			summary = "The categories of the compass",
			description = "The categories in their fixed order, localized. The structure is the same for every user; only the answers differ.")
	@APIResponse(responseCode = "200", description = "The categories, localized.")
	@GET
	@Path("categories")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<CategoryDto>> categories(@Context ContainerRequestContext crc, @Parameter(description = LANG_DESCRIPTION) @QueryParam("lang") String lang) {
		return compassReadService.findCategories(currentUser(crc), lang)
				.map(categories -> categories.stream().map(CategoryDto::from).toList());
	}

	@Operation(
			summary = "The questions of one category",
			description = "The questions of the category in their fixed order, each carrying the answer the user has given for it, or none. A category this application does not know answers an empty list.")
	@APIResponse(responseCode = "200", description = "The questions of the category with the user's answers.")
	@GET
	@Path("categories/{categoryId}/questions")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<QuestionDto>> categoryQuestions(
			@Context ContainerRequestContext crc,
			@Parameter(description = CATEGORY_ID_DESCRIPTION) @PathParam("categoryId") UUID categoryId,
			@Parameter(description = LANG_DESCRIPTION) @QueryParam("lang") String lang) {
		return compassReadService.findCategoryQuestions(currentUser(crc), lang, new CategoryIdImpl(categoryId.toString()))
				.map(questions -> questions.stream().map(QuestionDto::from).toList());
	}

	@Operation(
			summary = "The next question to put in front of the user in a category",
			description = "The lowest-position question of the category the user has not answered yet. When every question of it is answered, the answer says the category is complete and names no question. Navigation never crosses into another category.")
	@APIResponse(responseCode = "200", description = "The next question, or signal that the category is complete.")
	@GET
	@Path("categories/{categoryId}/next-question")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<NextQuestionResponse> nextQuestion(
			@Context ContainerRequestContext crc,
			@Parameter(description = CATEGORY_ID_DESCRIPTION) @PathParam("categoryId") UUID categoryId,
			@Parameter(description = LANG_DESCRIPTION) @QueryParam("lang") String lang) {
		return compassReadService.findNextQuestion(currentUser(crc), lang, new CategoryIdImpl(categoryId.toString()))
				.map(NextQuestionResponse::of);
	}

	@Operation(
			summary = "The question to step back to in a category",
			description = "The highest-position question of the category the user has answered, carrying the answer they gave so it can be shown already picked. When they have answered nothing there, the answer says they are at the start and names no question. The mirror of the next question, and equally confined to the one category.")
	@APIResponse(responseCode = "200", description = "The previous question with its answer, or flag that there is nothing to step back to.")
	@GET
	@Path("categories/{categoryId}/previous-question")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<PreviousQuestionResponse> previousQuestion(
			@Context ContainerRequestContext crc,
			@Parameter(description = CATEGORY_ID_DESCRIPTION) @PathParam("categoryId") UUID categoryId,
			@Parameter(description = LANG_DESCRIPTION) @QueryParam("lang") String lang) {
		return compassReadService.findPreviousQuestion(currentUser(crc), lang, new CategoryIdImpl(categoryId.toString()))
				.map(PreviousQuestionResponse::of);
	}

	@Operation(
			summary = "Every question of the compass, grouped by category",
			description = "The whole compass in one read: every question grouped under its category id, the categories in their usual order, each question carrying the user's answer or none — for a client that would rather hold it all than fetch it a category at a time.")
	@APIResponse(responseCode = "200", description = "Every question of every category, with the user's answers.")
	@GET
	@Path("questions")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<CategoryQuestionsResponse>> allQuestions(@Context ContainerRequestContext crc, @Parameter(description = LANG_DESCRIPTION) @QueryParam("lang") String lang) {
		return compassReadService.findAllQuestions(currentUser(crc), lang).map(CompassResource::groupByCategory);
	}

	private static List<CategoryQuestionsResponse> groupByCategory(List<AnsweredQuestion> questions) {
		var byCategory = new LinkedHashMap<CategoryId, List<QuestionDto>>();
		for (var answered : questions) {
			byCategory.computeIfAbsent(answered.question().getCategoryId(), k -> new ArrayList<>()).add(QuestionDto.from(answered));
		}
		return byCategory.entrySet().stream()
				.map(entry -> new CategoryQuestionsResponse(entry.getKey(), entry.getValue()))
				.toList();
	}
}
