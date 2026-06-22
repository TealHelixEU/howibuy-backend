package eu.tealhelix.howibuy.jaxrs.v1.assessment;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import eu.tealhelix.common.v1.model.User;
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
	public Uni<Response> assessSingleProduct(@Context ContainerRequestContext crc, ProductData productData) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return productAssessmentService.assessSingleProduct(user, productData)
				.map(t -> Response.ok(t).build());
	}

	@POST
	@Path("multi")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Response> assessMultipleProductsSync(@Context ContainerRequestContext crc, MultiProductAssessmentRequest request) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return productAssessmentService.assessMultipleProductsSync(user, request.products())
				.map(t -> Response.ok(new MultiProductAssessmentResponse(t)).build());
	}

	@POST
	@Path("multi-async")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Multi<ProductAssessmentOutcome> assessMultipleProductsAsync(@Context ContainerRequestContext crc, MultiProductAssessmentRequest request) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return RestMulti.fromMultiData(productAssessmentService.assessMultipleProductsAsync(user, request.products()))
				.encodeAsJsonArray(false).build();
	}
}
