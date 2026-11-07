package eu.tealhelix.howibuy.jaxrs.v1.dashboard;

import static eu.tealhelix.common.web.CommonOpenApiConstants.BEARER_AUTH;
import static eu.tealhelix.common.web.CommonOpenApiConstants.UNAUTHENTICATED;
import static eu.tealhelix.common.web.JaxRsUtils.currentUser;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.DASHBOARD;
import static eu.tealhelix.howibuy.jaxrs.v1.HowiBuyOpenApiTagNames.DASHBOARD_DESC;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.tealhelix.common.types.validation.BadInputValueException;
import eu.tealhelix.common.web.exceptionmap.SingleMessageResponse;
import eu.tealhelix.howibuy.services.v1.DashboardService;
import eu.tealhelix.howibuy.v1.model.Dashboard;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("dashboard")
@Tag(name = DASHBOARD, description = DASHBOARD_DESC)
@SecurityRequirement(name = BEARER_AUTH)
public class DashboardResource {
	@Inject
	DashboardService dashboardService;

	@Operation(
			summary = "Read the user's dashboard",
			description = "Everything the dashboard shows the authenticated end-user, in one read: their experience points total and the share of the other shoppers they are ahead of, the experience points of a product, their streak, the number of baskets they purchased, "
					+ "the averages of the products they bought in the month asked for and in the month before it, in each sustainability dimension, with the change between them, "
					+ "and the badges of each dimension, with the number of products that awards each and whether the user holds it. "
					+ "A purchase counts in the month of its time at the shop, as the Retailer stated it with the purchase.")
	@APIResponse(responseCode = "200", description = "The dashboard of the user. A user who has purchased nothing gets one too, with nothing counted.")
	@APIResponse(responseCode = "400", description = "The month is missing, or not written as `YYYY-MM`.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SingleMessageResponse.class)))
	@APIResponse(responseCode = "401", description = UNAUTHENTICATED)
	@APIResponse(responseCode = "403", description = "A service account presented the token: the dashboard belongs to an end-user.")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Dashboard> retrieveDashboard(
			@Context ContainerRequestContext crc,
			@Parameter(description = "The month the user calls this month, in their own time zone, written `YYYY-MM`. Its averages are compared with those of the month before it.", required = true, example = "2026-11")
			@QueryParam("month") String month,
			@Parameter(description = "The language of the badges' names, an ISO 639-1 code (`en`, `el`, ...). Left out or blank, English; a language without a translation falls back to English.")
			@QueryParam("lang") String lang
	) {
		var user = currentUser(crc);
		return Uni.createFrom().item(() -> yearMonth(month))
				.flatMap(yearMonth -> dashboardService.retrieveDashboard(user, yearMonth, lang));
	}

	/**
	 * @return The month written as {@code YYYY-MM}; {@code null} without one, which the service refuses
	 */
	private static YearMonth yearMonth(String month) {
		if (month == null) {
			return null;
		}
		try {
			return YearMonth.parse(month);
		} catch (DateTimeParseException e) {
			throw BadInputValueException.fromInputNameAndHint("month", "It must be written as YYYY-MM");
		}
	}
}
