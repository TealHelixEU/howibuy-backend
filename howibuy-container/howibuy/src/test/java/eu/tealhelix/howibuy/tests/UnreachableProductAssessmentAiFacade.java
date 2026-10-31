package eu.tealhelix.howibuy.tests;

import java.util.List;

import eu.tealhelix.howibuy.services.model.FoodTerm;
import eu.tealhelix.howibuy.services.v1.ai.AiSelection;
import eu.tealhelix.howibuy.services.v1.ai.impl.ProductAssessmentAiFacadeImpl;
import eu.tealhelix.howibuy.v1.model.ProductBaseData;
import io.smallrye.mutiny.Uni;

/**
 * Fails the request of an assessment that asks the language model anything.
 */
final class UnreachableProductAssessmentAiFacade extends ProductAssessmentAiFacadeImpl {
	UnreachableProductAssessmentAiFacade() {
		super(null, null);
	}

	@Override
	public Uni<AiSelection> extractL1Category(ProductBaseData productData, List<String> categories, List<FoodTerm> recognizedTerms) {
		throw new AssertionError("The assessment asked the language model");
	}

	@Override
	public Uni<AiSelection> extractSubcategory(ProductBaseData productData, List<String> categories, List<FoodTerm> recognizedTerms) {
		throw new AssertionError("The assessment asked the language model");
	}

	@Override
	public Uni<AiSelection> extractArchetypeProduct(ProductBaseData productData, List<String> products, List<FoodTerm> recognizedTerms, String categoryGuidance) {
		throw new AssertionError("The assessment asked the language model");
	}
}
