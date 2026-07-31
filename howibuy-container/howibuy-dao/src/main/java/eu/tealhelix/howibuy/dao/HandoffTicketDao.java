package eu.tealhelix.howibuy.dao;

import java.time.LocalDateTime;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.v1.types.HasUserId;
import eu.tealhelix.common.v1.types.UserId;
import io.smallrye.mutiny.Uni;

/**
 * DAO interface for the tickets that carry a user's session from a retailer application over to the single-page
 * application. A ticket is redeemable once, and only until it expires.
 */
public interface HandoffTicketDao {
	/**
	 * Store a ticket for a user to redeem once, until the given moment.
	 *
	 * @param tx         The reactive persistence context
	 * @param ticketHash The hash of the ticket; the ticket itself is known to whoever asked for it and to nobody else
	 * @param userId     The id of the user whose session the ticket opens
	 * @param expiresAt  The moment the ticket expires
	 */
	Uni<Void> createTicket(ReactivePersistenceTxContext tx, String ticketHash, HasUserId userId, LocalDateTime expiresAt);

	/**
	 * Redeem a ticket and name the user whose session it opens, refusing a ticket that does not exist, has expired, or
	 * has already been redeemed. All three are one answer here, so that the caller cannot tell them apart and neither can
	 * whoever is presenting the ticket.
	 *
	 * @param tx         The reactive persistence context
	 * @param ticketHash The hash of the ticket being presented
	 * @param now        The current moment, against which the ticket's expiration is read
	 * @return The id of the user whose session the ticket opens, or {@code null} if there was no ticket to redeem
	 */
	Uni<UserId> consumeTicket(ReactivePersistenceTxContext tx, String ticketHash, LocalDateTime now);

	/**
	 * Remove the tickets that can no longer be redeemed, so that the table holds no more than the tickets of the moment.
	 * A redeemed ticket goes with the rest once it expires: it is refused from the moment it is redeemed, and keeping it
	 * until then costs a row for as long as the ticket would have lived anyway.
	 *
	 * @param tx  The reactive persistence context
	 * @param now The current moment
	 * @return The number of tickets removed
	 */
	Uni<Integer> deleteExpiredTickets(ReactivePersistenceTxContext tx, LocalDateTime now);
}
