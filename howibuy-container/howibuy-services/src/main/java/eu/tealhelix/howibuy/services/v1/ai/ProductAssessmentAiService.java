package eu.tealhelix.howibuy.services.v1.ai;

import jakarta.enterprise.context.RequestScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "productAssessment")
@RequestScoped
public interface ProductAssessmentAiService {
	@SystemMessage(fromResource = "eu/tealhelix/howibuy/services/v1/ai/productAssessment-L1Category-system.md")
	@UserMessage(fromResource = "eu/tealhelix/howibuy/services/v1/ai/productAssessment-L1Category-user.md")
	String extractL1Category(String lang, String name, String characteristics, String tags, String categories);
}
