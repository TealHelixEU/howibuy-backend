package eu.tealhelix.howibuy.services.v1.ai;

import java.util.List;

import eu.tealhelix.howibuy.v1.model.ProductData;
import io.smallrye.mutiny.Uni;

/**
 * Facade for the product assessment AI services.
 * <p>
 * The facade adapts the synchronous calls to Langchain4J to the asynchronous nature of the business logic of the
 * application and renders the application model to strings that can be sent to the AI.
 */
public interface ProductAssessmentAiFacade {
	Uni<String> extractL1Category(ProductData productData, List<String> categories);
}
