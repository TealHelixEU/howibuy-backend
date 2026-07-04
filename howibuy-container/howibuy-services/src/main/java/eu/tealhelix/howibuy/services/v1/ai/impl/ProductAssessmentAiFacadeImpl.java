package eu.tealhelix.howibuy.services.v1.ai.impl;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.services.jee.impl.AiCircuitBreaker;
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
	public Uni<String> extractL1Category(ProductData productData, List<String> categories) {
		String lang = Objects.requireNonNull(productData.getLanguage()).getDisplayLanguage(Locale.ENGLISH);
		String characteristics = RenderingHelper.renderTheProductCharacteristics(Objects.requireNonNull(productData.getCharacteristics()));
		String tags = RenderingHelper.renderTheProductTags(Objects.requireNonNull(productData.getTags()));
		String categoriesStr = RenderingHelper.renderCategories(categories);
		Context callerContext = Vertx.currentContext();
		Uni<String> resultUni = Uni.createFrom().item(() -> aiCircuitBreaker.guard(() -> aiService.extractL1Category(lang, productData.getName(), characteristics, tags, categoriesStr)))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor());
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}
}
