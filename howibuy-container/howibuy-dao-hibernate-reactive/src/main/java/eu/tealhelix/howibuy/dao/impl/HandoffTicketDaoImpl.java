package eu.tealhelix.howibuy.dao.impl;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContext;
import eu.tealhelix.common.dao.reactive.ReactivePersistenceTxContext;
import eu.tealhelix.common.v1.types.HasUserId;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.HandoffTicketDao;
import eu.tealhelix.howibuy.dao.jpa.HandoffTicketEntity;
import eu.tealhelix.howibuy.dao.jpa.HandoffTicketEntity_;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity_;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class HandoffTicketDaoImpl implements HandoffTicketDao {
	@Override
	public Uni<Void> createTicket(ReactivePersistenceTxContext tx, String ticketHash, HasUserId userId, LocalDateTime expiresAt) {
		var ticket = new HandoffTicketEntity();
		ticket.setTicketHash(ticketHash);
		ticket.setUser(tx.getReference(UserProfileEntity.class, userId.getId().asUuid()));
		ticket.setExpiresAt(expiresAt);
		return tx.persist(ticket).replaceWithVoid();
	}

	@Override
	public Uni<UserId> consumeTicket(ReactivePersistenceTxContext tx, String ticketHash, LocalDateTime now) {
		return markConsumed(tx, ticketHash, now)
				.flatMap(consumed -> consumed == 1 ? findUserOfTicket(tx, ticketHash) : Uni.createFrom().nullItem());
	}

	/**
	 * One statement carries the whole decision to redeem, so that two requests presenting the same ticket cannot both
	 * pass it: the second one waits for the first to commit, reads the row again, and no longer matches it. Reading the
	 * row first and updating it afterwards would let both of them read it unconsumed.
	 *
	 * @return The number of tickets redeemed, which is one at most, the ticket hash being the primary key
	 */
	private Uni<Integer> markConsumed(ReactivePersistenceTxContext tx, String ticketHash, LocalDateTime now) {
		var cb = tx.getCriteriaBuilder();
		var u = cb.createCriteriaUpdate(HandoffTicketEntity.class);
		var ticket = u.from(HandoffTicketEntity.class);
		u.set(ticket.get(HandoffTicketEntity_.consumedAt), now);
		u.where(
				cb.equal(ticket.get(HandoffTicketEntity_.ticketHash), ticketHash),
				cb.isNull(ticket.get(HandoffTicketEntity_.consumedAt)),
				cb.greaterThan(ticket.get(HandoffTicketEntity_.expiresAt), now)
		);
		return tx.createUpdate(u).execute();
	}

	/**
	 * Read the user of a ticket that this transaction has just redeemed, so the row is there and is ours.
	 */
	private Uni<UserId> findUserOfTicket(ReactivePersistenceContext em, String ticketHash) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(UUID.class);
		var ticket = q.from(HandoffTicketEntity.class);
		q.select(ticket.get(HandoffTicketEntity_.user).get(UserProfileEntity_.id));
		q.where(cb.equal(ticket.get(HandoffTicketEntity_.ticketHash), ticketHash));
		return em.createQuery(q).getSingleResult().map(userId -> new UserIdImpl(userId.toString()));
	}

	@Override
	public Uni<Integer> deleteExpiredTickets(ReactivePersistenceTxContext tx, LocalDateTime now) {
		var cb = tx.getCriteriaBuilder();
		var d = cb.createCriteriaDelete(HandoffTicketEntity.class);
		var ticket = d.from(HandoffTicketEntity.class);
		d.where(cb.lessThanOrEqualTo(ticket.get(HandoffTicketEntity_.expiresAt), now));
		return tx.createDelete(d).execute();
	}
}
