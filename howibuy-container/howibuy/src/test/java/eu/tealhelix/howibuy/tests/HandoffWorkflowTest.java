package eu.tealhelix.howibuy.tests;

import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import jakarta.inject.Inject;

import eu.tealhelix.common.test.quarkus.InjectKeycloak;
import eu.tealhelix.common.web.authentication.jwt.TokenAuthenticationConfigImpl;
import eu.tealhelix.howibuy.services.v1.impl.HandoffServiceImpl;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * The whole handoff over the whole stack: the IDM authenticates a retailer's service account, the application
 * recognizes the retailer and either finds or creates the user behind the correlation id, hands out a ticket for that
 * user, and redeems it — once — for a session of the single-page application's own. That the mint succeeds is itself
 * the evidence the ticket reached the database.
 * <p>
 * Every redeem here is sent without an {@code Authorization} header, because the ticket is the only credential the
 * single-page application has at that point.
 */
@QuarkusTest
@WithRetailerIdm
public class HandoffWorkflowTest {
	private static final String CORRELATION_ID = "HANDOFF-1";

	/**
	 * Spelled in the alphabet of a real ticket, so that it is refused for not naming a ticket rather than for its shape.
	 */
	private static final String NEVER_MINTED_TICKET = "Zm9yZ2VkLXRpY2tldC10aGF0LW5vLW9uZS1ldmVyLW1pbnRlZA";

	@InjectKeycloak
	private GenericContainer<?> keycloak;

	@Inject
	@ConfigProperty(name = HandoffServiceImpl.TICKET_TIME_KEY)
	int ticketTimeInSeconds;

	@Inject
	@ConfigProperty(name = TokenAuthenticationConfigImpl.HANDOFF_SESSION_TIME_KEY)
	int handoffSessionTimeInSeconds;

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

	/**
	 * The session the ticket opens is a working one: the token it is redeemed for is accepted where a user's own token is
	 * expected, and it names the user the retailer sent over.
	 */
	@Test
	void aTicketIsRedeemedForAWorkingSession() {
		var redeemed = redeemTicket(mintedTicket())
				.statusCode(200)
				.contentType(JSON)
				.extract();

		assertEquals(handoffSessionTimeInSeconds, (int) redeemed.path("expires_in"), "how long the session token lasts");
		assertEquals(userOfCorrelationId(), GreetingPage.userId(redeemed.path("access_token")), "the user the retailer sent over");
	}

	@Test
	void aTicketIsRedeemedOnlyOnce() {
		var ticket = mintedTicket();

		redeemTicket(ticket).statusCode(200);
		redeemTicket(ticket).statusCode(401);
	}

	@Test
	void aTicketThatWasNeverMintedIsRefused() {
		redeemTicket(NEVER_MINTED_TICKET).statusCode(401);
	}

	@Test
	void aRedeemNamingNoTicketIsRejected() {
		redeemWithBody("{}").statusCode(400);
	}

	/**
	 * A ticket for this test's correlation id, minted the way a retailer mints one.
	 */
	private String mintedTicket() {
		return mintTicket(RetailerServiceAccount.accessToken(keycloak), CORRELATION_ID)
				.statusCode(200)
				.extract()
				.path("ticket");
	}

	/**
	 * The user this test's correlation id stands for, asked for through the token exchange — the other way a retailer
	 * reaches the same user, and one that says nothing about handoff.
	 */
	private String userOfCorrelationId() {
		String impersonationToken = RestAssured
				.given()
				.header("Authorization", "Bearer " + RetailerServiceAccount.accessToken(keycloak))
				.header("Content-Type", "application/json")
				.body("{\"correlationId\": \"" + CORRELATION_ID + "\"}")
				.when()
				.post("/api/howibuy/v1/tokenexchange")
				.then()
				.statusCode(200)
				.extract()
				.path("access_token");
		return GreetingPage.userId(impersonationToken);
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

	private static ValidatableResponse redeemTicket(String ticket) {
		return redeemWithBody("{\"ticket\": \"" + ticket + "\"}");
	}

	private static ValidatableResponse redeemWithBody(String body) {
		return RestAssured
				.given()
				.header("Content-Type", "application/json")
				.body(body)
				.when()
				.post("/api/howibuy/v1/handoff/redeem")
				.then();
	}
}
