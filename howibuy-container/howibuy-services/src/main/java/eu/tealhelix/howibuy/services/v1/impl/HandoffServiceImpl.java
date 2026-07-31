package eu.tealhelix.howibuy.services.v1.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import static eu.tealhelix.common.utils.UniComprehensions.forcm;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.types.authorization.NotAuthenticatedException;
import eu.tealhelix.common.types.validation.RequiredInputMissingException;
import eu.tealhelix.common.v1.types.HasUserId;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.howibuy.dao.HandoffTicketDao;
import eu.tealhelix.howibuy.services.v1.HandoffService;
import eu.tealhelix.howibuy.services.v1.types.IssuedTicket;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class HandoffServiceImpl implements HandoffService {
	private static final Logger LOG = LoggerFactory.getLogger(HandoffServiceImpl.class);

	public static final String TICKET_TIME_KEY = "config.handoff.ticketTimeInSeconds";

	/**
	 * The size of a ticket. A ticket is guessable in principle, being the sole credential of whoever presents it and
	 * accepted without any other, so it is drawn wide enough that guessing is not a way in.
	 */
	private static final int TICKET_BYTES = 32;

	private final HandoffTicketDao handoffTicketDao;
	private final DateTimeService dateTimeService;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final int ticketTimeInSeconds;
	private final SecureRandom random = new SecureRandom();

	@Inject
	public HandoffServiceImpl(
			HandoffTicketDao handoffTicketDao,
			DateTimeService dateTimeService,
			ReactivePersistenceContextFactory persistenceContextFactory,
			@ConfigProperty(name = TICKET_TIME_KEY) int ticketTimeInSeconds
	) {
		this.handoffTicketDao = handoffTicketDao;
		this.dateTimeService = dateTimeService;
		this.persistenceContextFactory = persistenceContextFactory;
		this.ticketTimeInSeconds = ticketTimeInSeconds;
	}

	@Override
	public Uni<IssuedTicket> mintTicket(HasUserId user) {
		return persistenceContextFactory.withTransaction(tx -> mintTicketInTx(tx, user));
	}

	private Uni<IssuedTicket> mintTicketInTx(ReactivePersistenceTxContext tx, HasUserId user) {
		var ticket = newTicket();
		var now = dateTimeService.getNow();
		var expiresAt = now.plusSeconds(ticketTimeInSeconds);
		return forcm(
				deleteExpiredTickets(tx, now),
				_ -> handoffTicketDao.createTicket(tx, hash(ticket), user, expiresAt),
				_ -> {
					LOG.info("Minted a handoff ticket for user {}, redeemable until {}", user.getId().asString(), expiresAt);
					return new IssuedTicket(ticket, ticketTimeInSeconds);
				}
		);
	}

	@Override
	public Uni<UserId> redeemTicket(String ticket) {
		RequiredInputMissingException.throwIfRequiredInputMissing("ticket", ticket);
		var ticketHash = hash(ticket);
		var now = dateTimeService.getNow();
		return persistenceContextFactory.withTransaction(tx -> handoffTicketDao.consumeTicket(tx, ticketHash, now))
				.onItem().ifNull().failWith(() -> refuseTicket(ticketHash))
				.invoke(userId -> LOG.info("Redeemed a handoff ticket for user {}", userId.asString()));
	}

	/**
	 * The application names a refused ticket by its hash, the only handle it has on one and not a thing anyone can present:
	 * one hash appearing again and again is a ticket being replayed, many hashes once each are guesses. Whoever presented
	 * it is told none of this, only that it did not work.
	 */
	private static NotAuthenticatedException refuseTicket(String ticketHash) {
		LOG.warn("Refused a handoff ticket that could not be redeemed, hash: {}", ticketHash);
		return new NotAuthenticatedException("the ticket cannot be redeemed");
	}

	/**
	 * Minting is what the table is visited for, so it is also when the tickets that can no longer be redeemed are cleared
	 * out of it. Nothing else in the application runs housekeeping on a clock, and a table that holds only the tickets of
	 * the moment needs nothing more than this.
	 */
	private Uni<Void> deleteExpiredTickets(ReactivePersistenceTxContext tx, LocalDateTime now) {
		return handoffTicketDao.deleteExpiredTickets(tx, now)
				.invoke(swept -> {
					if (swept > 0) {
						LOG.debug("Swept {} handoff tickets that could no longer be redeemed", swept);
					}
				})
				.replaceWithVoid();
	}

	/**
	 * A fresh ticket, drawn from a cryptographically strong source and spelled so that a URL carries it as it is: the
	 * retailer sends the user to the single-page application, and the ticket travels along.
	 */
	private String newTicket() {
		var bytes = new byte[TICKET_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * The hash of a ticket, which is all that is ever written down. The ticket has to be presented to be redeemed, so
	 * whoever reads the stored tickets holds nothing they can redeem.
	 */
	private static String hash(String ticket) {
		try {
			var sha256 = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(sha256.digest(ticket.getBytes(UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required of every Java runtime", e);
		}
	}
}
