package eu.tealhelix.howibuy.services.v1.types;

/**
 * A handoff ticket as it is handed to the retailer that asked for it, together with how long it stays redeemable. This
 * is the only copy of the ticket there will ever be: the application keeps its hash and cannot produce it again.
 *
 * @param ticket           The ticket
 * @param expiresInSeconds The seconds from the moment of issue until the ticket can no longer be redeemed
 */
public record IssuedTicket(String ticket, int expiresInSeconds) {
}
