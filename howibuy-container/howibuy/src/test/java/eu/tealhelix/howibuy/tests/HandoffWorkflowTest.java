package eu.tealhelix.howibuy.tests;

import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import jakarta.inject.Inject;

import eu.tealhelix.common.test.quarkus.InjectKeycloak;
import eu.tealhelix.howibuy.services.v1.impl.HandoffServiceImpl;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * A retailer asking for a handoff ticket, over the whole stack: the IDM authenticates its service account, the
 * application recognises the retailer and either finds or creates the user behind the correlation id, and a ticket is
 * stored for that user. That the request succeeds is itself the evidence the ticket reached the database.
 */
@QuarkusTest
@WithRetailerIdm
public class HandoffWorkflowTest {
	private static final String CORRELATION_ID = "HANDOFF-1";

	@InjectKeycloak
	private GenericContainer<?> keycloak;

	@Inject
	@ConfigProperty(name = HandoffServiceImpl.TICKET_TIME_KEY)
	int ticketTimeInSeconds;

	@Test
	void aRetailerIsHandedATicketForOneOfItsUsers() {
		var response = mintTicket(RetailerServiceAccount.accessToken(keycloak), CORRELATION_ID)
				.statusCode(200)
				.contentType(JSON)
				.extract();

		assertFalse(response.<String>path("ticket").isBlank(), "the ticket the retailer sends the user along with");
		assertEquals(ticketTimeInSeconds, (int) response.path("expires_in"), "how long the retailer is told it lasts");
	}

	/**
	 * The ticket is a credential, so each one is a fresh secret; a retailer asking twice for the same user is asking for
	 * two sessions, not for the same one again.
	 */
	@Test
	void everyTicketIsHandedOutOnce() {
		var serviceAccessToken = RetailerServiceAccount.accessToken(keycloak);

		var first = mintTicket(serviceAccessToken, CORRELATION_ID).statusCode(200).extract().path("ticket");
		var second = mintTicket(serviceAccessToken, CORRELATION_ID).statusCode(200).extract().path("ticket");

		assertNotEquals(first, second, "two tickets for the same user");
	}

	@Test
	void aTicketIsRefusedToACallerThatIsNoRetailer() {
		mintTicket(null, CORRELATION_ID).statusCode(403);
	}

	private static ValidatableResponse mintTicket(String serviceAccessToken, String correlationId) {
		var request = RestAssured.given().header("Content-Type", "application/json");
		if (serviceAccessToken != null) {
			request = request.header("Authorization", "Bearer " + serviceAccessToken);
		}
		return request
				.body("{\"correlationId\": \"" + correlationId + "\"}")
				.when()
				.post("/api/howibuy/v1/handoff")
				.then();
	}
}
