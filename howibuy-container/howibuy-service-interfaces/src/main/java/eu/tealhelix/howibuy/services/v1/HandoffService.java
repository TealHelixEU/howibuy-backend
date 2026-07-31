package eu.tealhelix.howibuy.services.v1;

import eu.tealhelix.common.v1.types.HasUserId;
import eu.tealhelix.howibuy.services.v1.types.IssuedTicket;
import io.smallrye.mutiny.Uni;

/**
 * The tickets that carry a user's session from a retailer application over to the single-page application. A retailer
 * asks for one and sends the user along with it; the single-page application redeems it for a session of its own.
 */
public interface HandoffService {
	/**
	 * Mint a ticket for a user. The ticket travels through the user's browser and is therefore worth as little as it can
	 * be made to be worth: it names no session of its own, it is redeemable for a short while only, and once.
	 *
	 * @param user The user whose session the ticket opens
	 * @return The ticket, which the application will not be able to produce a second time
	 */
	Uni<IssuedTicket> mintTicket(HasUserId user);
}
