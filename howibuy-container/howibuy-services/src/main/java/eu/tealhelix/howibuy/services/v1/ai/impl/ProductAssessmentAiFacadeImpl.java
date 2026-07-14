package eu.tealhelix.howibuy.services.v1.ai.impl;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.services.jee.impl.AiCircuitBreaker;
import eu.tealhelix.howibuy.services.model.FoodTerm;
import eu.tealhelix.howibuy.services.v1.ai.AiSelection;
import eu.tealhelix.howibuy.services.v1.ai.ProductAssessmentAiFacade;
import eu.tealhelix.howibuy.services.v1.ai.ProductAssessmentAiService;
import eu.tealhelix.howibuy.v1.model.ProductData;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

@ApplicationScoped
public class ProductAssessmentAiFacadeImpl implements ProductAssessmentAiFacade {
	private final ProductAssessmentAiService aiService;
	private final AiCircuitBreaker aiCircuitBreaker;

	public ProductAssessmentAiFacadeImpl(ProductAssessmentAiService aiService, AiCircuitBreaker aiCircuitBreaker) {
		this.aiService = aiService;
		this.aiCircuitBreaker = aiCircuitBreaker;
	}

	@Override
	public Uni<AiSelection> extractL1Category(ProductData productData, List<String> categories, List<FoodTerm> recognizedTerms) {
		return classify(productData, categories, recognizedTerms, aiService::extractL1Category);
	}

	@Override
	public Uni<AiSelection> extractSubcategory(ProductData productData, List<String> categories, List<FoodTerm> recognizedTerms) {
		return classify(productData, categories, recognizedTerms, aiService::extractSubcategory);
	}

	@Override
	public Uni<AiSelection> extractArchetypeProduct(ProductData productData, List<String> products, List<FoodTerm> recognizedTerms) {
		return classify(productData, products, recognizedTerms, aiService::extractArchetypeProduct);
	}

	/**
	 * Renders the product, the recognized glossary terms and the candidate names to the flat strings the AI templates
	 * expect, invokes the guarded (blocking) AI call off the event loop, parses the reply into an {@link AiSelection},
	 * and re-emits the result on the caller's Vert.x context.
	 */
	private Uni<AiSelection> classify(ProductData productData, List<String> candidates, List<FoodTerm> recognizedTerms, AiCall aiCall) {
		String lang = Objects.requireNonNull(productData.getLanguage()).getDisplayLanguage(Locale.ENGLISH);
		String enrichment = RenderingHelper.renderEnrichment(recognizedTerms);
		String characteristics = RenderingHelper.renderTheProductCharacteristics(Objects.requireNonNull(productData.getCharacteristics()));
		String tags = RenderingHelper.renderTheProductTags(Objects.requireNonNull(productData.getTags()));
		String candidatesStr = RenderingHelper.renderCandidates(candidates);
		Context callerContext = Vertx.currentContext();
		Uni<AiSelection> resultUni = Uni.createFrom().item(() -> aiCircuitBreaker.guard(() -> aiCall.call(lang, productData.getName(), enrichment, characteristics, tags, candidatesStr)))
				.map(reply -> RenderingHelper.parseSelection(reply, candidates.size()))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor());
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}

	@FunctionalInterface
	private interface AiCall {
		String call(String lang, String name, String enrichment, String characteristics, String tags, String candidates);
	}
}
