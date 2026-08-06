package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import static eu.tealhelix.common.web.JaxRsUtils.currentUser;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.howibuy.services.v1.ProductAssessmentService;
import eu.tealhelix.howibuy.v1.model.ProductAssessmentOutcome;
import eu.tealhelix.howibuy.v1.model.ProductData;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.RestMulti;

@Path("assessment")
public class ProductAssessmentResource {
	@Inject
	ProductAssessmentService productAssessmentService;

	@POST
	@Path("single")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<ProductAssessmentOutcome> assessSingleProduct(@Context ContainerRequestContext crc, ProductData productData) {
		return productAssessmentService.assessSingleProduct(currentUser(crc), productData);
	}

	@POST
	@Path("multi")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<MultiProductAssessmentResponse> assessMultipleProductsSync(@Context ContainerRequestContext crc, MultiProductAssessmentRequest request) {
		return productAssessmentService.assessMultipleProductsSync(currentUser(crc), request.products())
				.map(MultiProductAssessmentResponse::new);
	}

	@POST
	@Path("multi-async")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Multi<ProductAssessmentOutcome> assessMultipleProductsAsync(@Context ContainerRequestContext crc, MultiProductAssessmentRequest request) {
		return RestMulti.fromMultiData(productAssessmentService.assessMultipleProductsAsync(currentUser(crc), request.products()))
				.encodeAsJsonArray(false).build();
	}
}
