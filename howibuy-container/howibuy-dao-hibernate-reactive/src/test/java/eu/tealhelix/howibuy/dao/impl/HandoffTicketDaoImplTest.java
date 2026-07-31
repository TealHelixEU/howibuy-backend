package eu.tealhelix.howibuy.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import eu.tealhelix.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.common.v1.types.UserId;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.howibuy.dao.jpa.UserProfileEntity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.core.Vertx;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The single use of a handoff ticket is what stands between a leaked ticket and a stolen session, so it is tested
 * against a real database: the guarantee is one of the database's, not of the arithmetic above it.
 */
@Testcontainers
public class HandoffTicketDaoImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	/**
	 * How long the request that redeems the ticket first holds the row, giving the second one time to reach the database
	 * and start waiting for it. Both requests reach the same verdict whether or not it gets there in time; the wait is
	 * what makes the contended path the one being exercised.
	 */
	private static final long CONTENTION_MILLIS = 500;

	private static final UUID USER_UUID = UUID.fromString("2e788895-0503-4777-a7bd-24e5d61db5b1");
	private static final UserId USER = new UserIdImpl(USER_UUID.toString());

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 12, 0);

	private static final HandoffTicketDaoImpl sut = new HandoffTicketDaoImpl();

	private static ReactivePersistenceContextFactory factory;

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "howibuy.db.changelog.xml", "test");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	/**
	 * Every ticket points at a user, so one has to exist. Each test then mints the tickets it needs under its own hashes
	 * and none of them looks at another's.
	 */
	@BeforeAll
	static void seedUser(Mutiny.SessionFactory sessionFactory) {
		factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var user = new UserProfileEntity();
		user.setId(USER_UUID);
		factory.withTransaction(tx -> tx.persist(user)).await().atMost(WAIT);
	}

	@Test
	void aHashThatNamesNoTicketRedeemsNobody() {
		assertNull(consume("no-such-ticket", NOW), "nothing to redeem");
	}

	@Test
	void aLiveTicketNamesTheUserItWasMintedFor() {
		givenTicket("live", NOW.plusMinutes(1));

		assertEquals(USER, consume("live", NOW), "the user of the ticket");
	}

	@Test
	void aTicketIsRedeemedOnlyOnce() {
		givenTicket("once", NOW.plusMinutes(1));

		assertEquals(USER, consume("once", NOW), "the request that got there first");
		assertNull(consume("once", NOW), "a second request presenting the same ticket");
	}

	@Test
	void aTicketIsNoLongerRedeemableAtTheMomentItExpires() {
		givenTicket("expired", NOW);

		assertNull(consume("expired", NOW), "a ticket whose expiry has arrived");
	}

	/**
	 * Two requests present the same ticket at once, the second reaching the database while the first still holds the row.
	 * Only one of them may be told the user: the waiting request reads the row again once the first one commits, and by
	 * then it is taken.
	 */
	@Test
	void onlyOneOfTwoRequestsRacingForTheSameTicketRedeemsIt() throws Exception {
		givenTicket("raced", NOW.plusMinutes(1));
		var firstHasTheRow = new CompletableFuture<Void>();

		var first = factory.withTransaction(tx -> sut.consumeTicket(tx, "raced", NOW)
						.call(() -> holdTheRow(firstHasTheRow)))
				.subscribe().withSubscriber(UniAssertSubscriber.create());
		firstHasTheRow.get(WAIT.toSeconds(), SECONDS);

		var second = factory.withTransaction(tx -> sut.consumeTicket(tx, "raced", NOW))
				.subscribe().withSubscriber(UniAssertSubscriber.create());

		assertEquals(USER, first.awaitItem(WAIT).getItem(), "the request that reached the ticket first");
		assertNull(second.awaitItem(WAIT).getItem(), "the request that found it taken");
	}

	/**
	 * The sweep is the one operation that reads the whole table, so its tickets live in an hour of their own: every other
	 * test's ticket expires later than the moment this one sweeps, and is therefore none of its business.
	 */
	@Test
	void theSweepRemovesTheTicketsThatCanNoLongerBeRedeemed() {
		var sweptAt = NOW.minusHours(1);
		givenTicket("swept-long-gone", sweptAt.minusMinutes(5));
		givenTicket("swept-just-expired", sweptAt);
		givenTicket("swept-still-live", sweptAt.plusMinutes(1));

		assertEquals(2, sweep(sweptAt), "the two tickets that had expired");
		assertEquals(USER, consume("swept-still-live", sweptAt), "the live ticket outlasted the sweep");
	}

	/**
	 * Keep the transaction that has just redeemed the ticket open for a while, so that another request meets the row while
	 * it is both taken and locked. The wait ends on the event loop the transaction was opened on, because that is the only
	 * thread Hibernate Reactive will let it commit from.
	 *
	 * @param hasTheRow Completed once the row is held, to be awaited by whoever is about to contend for it
	 */
	private static Uni<Void> holdTheRow(CompletableFuture<Void> hasTheRow) {
		var released = new CompletableFuture<Void>();
		Vertx.currentContext().owner().setTimer(CONTENTION_MILLIS, ignored -> released.complete(null));
		hasTheRow.complete(null);
		return Uni.createFrom().completionStage(released);
	}

	private static void givenTicket(String ticketHash, LocalDateTime expiresAt) {
		factory.withTransaction(tx -> sut.createTicket(tx, ticketHash, USER, expiresAt)).await().atMost(WAIT);
	}

	private static UserId consume(String ticketHash, LocalDateTime now) {
		return factory.withTransaction(tx -> sut.consumeTicket(tx, ticketHash, now)).await().atMost(WAIT);
	}

	private static int sweep(LocalDateTime now) {
		return factory.withTransaction(tx -> sut.deleteExpiredTickets(tx, now)).await().atMost(WAIT);
	}
}
