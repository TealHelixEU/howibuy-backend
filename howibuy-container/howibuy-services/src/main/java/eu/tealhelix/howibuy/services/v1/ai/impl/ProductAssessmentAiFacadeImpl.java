package eu.tealhelix.howibuy.services.v1.ai.impl;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.services.jee.impl.AiCircuitBreaker;
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
	public Uni<AiSelection> extractL1Category(ProductData productData, List<String> categories) {
		return classify(productData, categories, aiService::extractL1Category);
	}

	@Override
	public Uni<AiSelection> extractSubcategory(ProductData productData, List<String> categories) {
		return classify(productData, categories, aiService::extractSubcategory);
	}

	@Override
	public Uni<AiSelection> extractArchetypeProduct(ProductData productData, List<String> products) {
		return classify(productData, products, aiService::extractArchetypeProduct);
	}

	/**
	 * Renders the product and the candidate names to the flat strings the AI templates expect, invokes the guarded
	 * (blocking) AI call off the event loop, parses the reply into an {@link AiSelection}, and re-emits the result on
	 * the caller's Vert.x context.
	 */
	private Uni<AiSelection> classify(ProductData productData, List<String> candidates, AiCall aiCall) {
		String lang = Objects.requireNonNull(productData.getLanguage()).getDisplayLanguage(Locale.ENGLISH);
		String characteristics = RenderingHelper.renderTheProductCharacteristics(Objects.requireNonNull(productData.getCharacteristics()));
		String tags = RenderingHelper.renderTheProductTags(Objects.requireNonNull(productData.getTags()));
		String candidatesStr = RenderingHelper.renderCandidates(candidates);
		Context callerContext = Vertx.currentContext();
		Uni<AiSelection> resultUni = Uni.createFrom().item(() -> aiCircuitBreaker.guard(() -> aiCall.call(lang, productData.getName(), characteristics, tags, candidatesStr)))
				.map(reply -> RenderingHelper.parseSelection(reply, candidates.size()))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor());
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}

	@FunctionalInterface
	private interface AiCall {
		String call(String lang, String name, String characteristics, String tags, String candidates);
	}
}
