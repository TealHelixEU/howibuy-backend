package eu.tealhelix.howibuy.services.v1.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static eu.tealhelix.common.v1.types.UserIdTestUtils.matchesHasUserId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.types.authorization.NotAuthenticatedException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.HandoffTicketDao;
import eu.tealhelix.howibuy.services.v1.types.IssuedTicket;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * What the retailer is handed and what the database is told, with the database mocked: the ticket is unguessable, only
 * its hash is kept, and it may be redeemed for as long as the configuration says. That a ticket is then redeemed exactly
 * once is the database's guarantee, covered by {@code HandoffTicketDaoImplTest}.
 */
@ExtendWith(MockitoExtension.class)
public class HandoffServiceImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final int TICKET_TIME_IN_SECONDS = 60;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 12, 0);
	private static final String TICKET = "the-ticket-as-it-was-presented";
	private static final User USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, false);

	@Mock
	HandoffTicketDao handoffTicketDao;

	@Mock
	DateTimeService dateTimeService;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	private HandoffServiceImpl sut;

	@BeforeEach
	void setUp() {
		sut = new HandoffServiceImpl(handoffTicketDao, dateTimeService, persistenceContextFactory, TICKET_TIME_IN_SECONDS);
	}

	@Test
	void onlyTheHashOfTheTicketIsStored() throws Exception {
		givenTicketsAreStored();

		var issued = mintTicket();

		var stored = storedHash();
		assertEquals(sha256Hex(issued.ticket()), stored, "the SHA-256 of the ticket handed out, hex encoded");
		assertEquals(64, stored.length(), "the width of the ticket_hash column");
	}

	@Test
	void everyTicketIsDifferent() {
		givenTicketsAreStored();

		var first = mintTicket();
		var second = mintTicket();

		assertNotEquals(first.ticket(), second.ticket(), "two tickets minted one after the other");
	}

	@Test
	void aTicketCarriesTooMuchRandomnessToBeGuessed() {
		givenTicketsAreStored();

		var ticket = mintTicket().ticket();

		assertEquals(32, Base64.getUrlDecoder().decode(ticket).length, "the bytes of randomness behind the ticket");
	}

	/**
	 * The ticket reaches the single-page application through the user's browser, so it is spelled in the alphabet a URL
	 * carries as it is.
	 */
	@Test
	void aTicketNeedsNoEscaping() {
		givenTicketsAreStored();

		var ticket = mintTicket().ticket();

		assertTrue(ticket.matches("[A-Za-z0-9_-]+"), "the ticket is spelled with " + ticket);
	}

	@Test
	void aTicketIsMintedForTheUserAndExpiresAfterTheConfiguredTime() {
		givenTicketsAreStored();

		mintTicket();

		verify(handoffTicketDao).createTicket(any(), any(), matchesHasUserId(USER), eq(NOW.plusSeconds(TICKET_TIME_IN_SECONDS)));
	}

	@Test
	void theTicketSaysHowLongItMayBeRedeemed() {
		givenTicketsAreStored();

		assertEquals(TICKET_TIME_IN_SECONDS, mintTicket().expiresInSeconds(), "the configured ticket time");
	}

	/**
	 * The table is swept as tickets are minted, which is the only thing that ever visits all of it; there is nothing else
	 * in the application to run housekeeping on a clock.
	 */
	@Test
	void mintingATicketSweepsTheTicketsThatCanNoLongerBeRedeemed() {
		givenTicketsAreStored();

		mintTicket();

		verify(handoffTicketDao).deleteExpiredTickets(any(), eq(NOW));
		assertEquals(1, persistenceContextFactory.getOpenedTransactions().size(), "the sweep and the new ticket share one transaction");
	}

	@Test
	void aTicketIsRedeemedByItsHashAtTheMomentItIsPresented() throws Exception {
		givenTheTicketRedeems(sha256Hex(TICKET), USER.getId());

		assertEquals(USER.getId(), redeemTicket(TICKET), "the user whose session the ticket opens");
	}

	/**
	 * A ticket that never existed, one that has expired and one that was already redeemed reach the database as the same
	 * question and come back as the same answer, so whoever presents a ticket learns only that it did not work.
	 */
	@Test
	void aTicketThatTheDatabaseRefusesIsRefused() {
		givenNoTicketRedeems();

		var failure = sut.redeemTicket(TICKET).subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitFailure(WAIT).getFailure();

		assertInstanceOf(NotAuthenticatedException.class, failure, "a ticket that could not be redeemed");
	}

	@Test
	void aRedeemWithoutATicketIsRejectedBeforeTheDatabaseIsAsked() {
		assertThrows(RequiredInputMissingException.class, () -> redeemTicket(null), "no ticket to redeem");

		verifyNoInteractions(handoffTicketDao);
	}

	/**
	 * A database that takes the tickets it is given, which is all the minting asks of it.
	 */
	private void givenTicketsAreStored() {
		givenTheClockStandsAtNow();
		when(handoffTicketDao.createTicket(any(), any(), any(), any())).thenReturn(Uni.createFrom().voidItem());
		when(handoffTicketDao.deleteExpiredTickets(any(), any())).thenReturn(Uni.createFrom().item(0));
	}

	private void givenTheTicketRedeems(String ticketHash, UserId userId) {
		givenTheClockStandsAtNow();
		when(handoffTicketDao.consumeTicket(any(), eq(ticketHash), eq(NOW))).thenReturn(Uni.createFrom().item(userId));
	}

	private void givenNoTicketRedeems() {
		givenTheClockStandsAtNow();
		when(handoffTicketDao.consumeTicket(any(), any(), any())).thenReturn(Uni.createFrom().nullItem());
	}

	private void givenTheClockStandsAtNow() {
		when(dateTimeService.getNow()).thenReturn(NOW);
	}

	private IssuedTicket mintTicket() {
		return sut.mintTicket(USER).await().atMost(WAIT);
	}

	private UserId redeemTicket(String ticket) {
		return sut.redeemTicket(ticket).await().atMost(WAIT);
	}

	/**
	 * The ticket hash the database was told, read back from the call that told it.
	 */
	private String storedHash() {
		var hash = ArgumentCaptor.forClass(String.class);
		verify(handoffTicketDao).createTicket(any(), hash.capture(), any(), any());
		return hash.getValue();
	}

	/**
	 * Computed here rather than borrowed from the implementation, so that the two have to agree.
	 */
	private static String sha256Hex(String value) throws NoSuchAlgorithmException {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8)));
	}
}
