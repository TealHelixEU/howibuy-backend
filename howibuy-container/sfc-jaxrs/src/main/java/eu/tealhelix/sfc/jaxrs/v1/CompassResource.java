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

import eu.tealhelix.sfc.services.v1.CompassStructureService;
import eu.tealhelix.sfc.v1.model.Question;
import io.smallrye.mutiny.Uni;

/**
 * Reads of the Sustainable Food Compass's fixed structure — its categories and questions — localized via an optional
 * {@code ?lang} query parameter (defaulting to the configured default language). All reads require an authenticated
 * end-user.
 */
@Path("sfc")
public class CompassResource {
	@Inject
	CompassStructureService compassStructureService;

	@GET
	@Path("categories")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<CategoryResponse>> categories(@Context ContainerRequestContext crc, @QueryParam("lang") String lang) {
		return compassStructureService.findCategories(currentUser(crc), lang)
				.map(categories -> categories.stream().map(CategoryResponse::from).toList());
	}

	@GET
	@Path("categories/{categoryId}/questions")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<QuestionResponse>> categoryQuestions(@Context ContainerRequestContext crc, @PathParam("categoryId") UUID categoryId, @QueryParam("lang") String lang) {
		return compassStructureService.findCategoryQuestions(currentUser(crc), lang, categoryId)
				.map(questions -> questions.stream().map(QuestionResponse::from).toList());
	}

	@GET
	@Path("questions")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<CategoryQuestionsResponse>> allQuestions(@Context ContainerRequestContext crc, @QueryParam("lang") String lang) {
		return compassStructureService.findAllQuestions(currentUser(crc), lang).map(CompassResource::groupByCategory);
	}

	private static List<CategoryQuestionsResponse> groupByCategory(List<Question> questions) {
		var byCategory = new LinkedHashMap<UUID, List<QuestionResponse>>();
		for (var question : questions) {
			byCategory.computeIfAbsent(question.getCategoryId(), k -> new ArrayList<>()).add(QuestionResponse.from(question));
		}
		return byCategory.entrySet().stream()
				.map(entry -> new CategoryQuestionsResponse(entry.getKey(), entry.getValue()))
				.toList();
	}
}
