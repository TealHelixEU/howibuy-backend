package eu.tealhelix.betterme;

import eu.tealhelix.common.v1.model.User;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("greeting")
public class GreetingResource {
	@Inject
	@Location("rest/greeting.html")
	Template greeting;

	@GET
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance getHtml(@Context ContainerRequestContext crc, @QueryParam("name") String name) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return greeting.data("name", user.getName());
	}
}
