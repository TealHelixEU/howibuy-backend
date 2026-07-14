package eu.tealhelix.howibuy.services.v1.ai;

import java.util.List;

import eu.tealhelix.howibuy.services.model.FoodTerm;
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
 * <p>
 * {@code recognizedTerms} are the glossary terms found in the product name; the facade renders them into the enrichment
 * the AI sees, giving it world knowledge the model may lack. They are the same at every level of the descent.
 */
public interface ProductAssessmentAiFacade {
	Uni<AiSelection> extractL1Category(ProductData productData, List<String> categories, List<FoodTerm> recognizedTerms);

	Uni<AiSelection> extractSubcategory(ProductData productData, List<String> categories, List<FoodTerm> recognizedTerms);

	Uni<AiSelection> extractArchetypeProduct(ProductData productData, List<String> products, List<FoodTerm> recognizedTerms);
}
