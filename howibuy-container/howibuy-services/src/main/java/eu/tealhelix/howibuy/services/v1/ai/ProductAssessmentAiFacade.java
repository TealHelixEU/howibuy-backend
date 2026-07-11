package eu.tealhelix.howibuy.services.v1.ai;

import java.util.List;

import eu.tealhelix.howibuy.v1.model.ProductData;
import io.smallrye.mutiny.Uni;

/**
 * Facade for the product assessment AI services.
 * <p>
 * The facade adapts the synchronous calls to Langchain4J to the asynchronous nature of the business logic of the
 * application and renders the application model to strings that can be sent to the AI.
 * <p>
 * Each method shows the AI the {@code candidates} as a numbered list and returns its pick as an {@link AiSelection} — a
 * {@link AiSelection.Match} carrying the {@code 0}-based index into {@code candidates}, {@link AiSelection.None} when
 * the AI reports no match, or {@link AiSelection.Malformed} when the reply is neither. Resolving the pick by index
 * rather than by the candidate's text keeps the match robust against a model that alters the text it echoes back (see
 * {@link AiSelection}).
 */
public interface ProductAssessmentAiFacade {
	Uni<AiSelection> extractL1Category(ProductData productData, List<String> categories);

	Uni<AiSelection> extractSubcategory(ProductData productData, List<String> categories);

	Uni<AiSelection> extractArchetypeProduct(ProductData productData, List<String> products);
}
