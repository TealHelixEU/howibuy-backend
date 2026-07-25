package eu.tealhelix.sfc.jaxrs.v1;

import static eu.tealhelix.common.web.JaxRsUtils.currentUser;

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

import eu.tealhelix.sfc.services.v1.CompassReadService;
import eu.tealhelix.sfc.services.v1.types.AnsweredQuestion;
import eu.tealhelix.sfc.v1.types.CategoryId;
import eu.tealhelix.sfc.v1.types.impl.CategoryIdImpl;
import io.smallrye.mutiny.Uni;

/**
 * Reads of the Sustainable Food Compass for the authenticated end-user, localized via an optional {@code ?lang} query
 * parameter (defaulting to the configured default language): its categories and questions, each question paired with
 * the user's current answer, and the "next question" that guides them forward through a category. All reads require an
 * authenticated end-user.
 */
@Path("sfc")
public class CompassResource {
	@Inject
	CompassReadService compassReadService;

	@GET
	@Path("categories")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<CategoryResponse>> categories(@Context ContainerRequestContext crc, @QueryParam("lang") String lang) {
		return compassReadService.findCategories(currentUser(crc), lang)
				.map(categories -> categories.stream().map(CategoryResponse::from).toList());
	}

	@GET
	@Path("categories/{categoryId}/questions")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<QuestionResponse>> categoryQuestions(@Context ContainerRequestContext crc, @PathParam("categoryId") UUID categoryId, @QueryParam("lang") String lang) {
		return compassReadService.findCategoryQuestions(currentUser(crc), lang, new CategoryIdImpl(categoryId.toString()))
				.map(questions -> questions.stream().map(QuestionResponse::from).toList());
	}

	@GET
	@Path("categories/{categoryId}/next-question")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<NextQuestionResponse> nextQuestion(@Context ContainerRequestContext crc, @PathParam("categoryId") UUID categoryId, @QueryParam("lang") String lang) {
		return compassReadService.findNextQuestion(currentUser(crc), lang, new CategoryIdImpl(categoryId.toString()))
				.map(NextQuestionResponse::of);
	}

	@GET
	@Path("questions")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<CategoryQuestionsResponse>> allQuestions(@Context ContainerRequestContext crc, @QueryParam("lang") String lang) {
		return compassReadService.findAllQuestions(currentUser(crc), lang).map(CompassResource::groupByCategory);
	}

	private static List<CategoryQuestionsResponse> groupByCategory(List<AnsweredQuestion> questions) {
		var byCategory = new LinkedHashMap<CategoryId, List<QuestionResponse>>();
		for (var answered : questions) {
			byCategory.computeIfAbsent(answered.question().getCategoryId(), k -> new ArrayList<>()).add(QuestionResponse.from(answered));
		}
		return byCategory.entrySet().stream()
				.map(entry -> new CategoryQuestionsResponse(entry.getKey(), entry.getValue()))
				.toList();
	}
}
