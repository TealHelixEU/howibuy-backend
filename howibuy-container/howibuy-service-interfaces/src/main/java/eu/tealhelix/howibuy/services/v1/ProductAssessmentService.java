package eu.tealhelix.howibuy.services.v1;

import java.util.List;

import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.ProductKey;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

public interface ProductAssessmentService {
	Uni<ProductAssessmentOutcome> assessSingleProduct(User user, CatalogueId catalogueId, ProductKey productKey);

	Uni<List<ProductAssessmentOutcome>> assessMultipleProductsSync(User user, CatalogueId catalogueId, List<ProductKey> productKeys);

	Multi<ProductAssessmentOutcome> assessMultipleProductsAsync(User user, CatalogueId catalogueId, List<ProductKey> productKeys);
}
