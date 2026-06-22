package eu.tealhelix.howibuy.services.v1;

import java.util.List;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductData;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

public interface ProductAssessmentService {
	Uni<ProductAssessmentOutcome> assessSingleProduct(User user, ProductData productData);

	Uni<List<ProductAssessmentOutcome>> assessMultipleProductsSync(User user, List<ProductData> productsData);

	Multi<ProductAssessmentOutcome> assessMultipleProductsAsync(User user, List<ProductData> productsData);
}
