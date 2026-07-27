package eu.tealhelix.sfc.dao.impl;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import eu.tealhelix.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.tealhelix.common.test.jpa.HibernateReactiveExtension;
import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import eu.tealhelix.sfc.dao.jpa.AttemptEntity;
import eu.tealhelix.sfc.v1.types.AttemptStatus;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The attempt DAO against a real database: {@code findInProgressId} returns a user's single in-progress attempt, empty
 * when they have none, and ignores completed attempts (and other users' attempts); {@code startInProgress} inserts a
 * fresh in-progress attempt that the finder then locates; {@code complete} freezes it as a completed record; and
 * {@code findLatestCompletedAt} reports the most recent completion (ignoring in-progress attempts) for the stability
 * window. The schema is created from the SFC changelog via the test stub that supplies the cross-module
 * {@code TH_USER_PROFILE}; users are seeded over JDBC since the profile is not a JPA entity in this module.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class AttemptDaoImplTest {
	private static final Duration WAIT = Duration.ofSeconds(300);

	private static final UUID USER_WITHOUT_ATTEMPT = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	private static final UUID USER_TO_START = UUID.fromString("d0000000-0000-0000-0000-000000000002");
	private static final UUID USER_WITH_COMPLETED_ONLY = UUID.fromString("d0000000-0000-0000-0000-000000000003");
	private static final UUID USER_TO_COMPLETE = UUID.fromString("d0000000-0000-0000-0000-000000000004");
	private static final UUID USER_WITH_HISTORY = UUID.fromString("d0000000-0000-0000-0000-000000000005");

	private static final LocalDateTime EARLIER_COMPLETION = LocalDateTime.of(2026, 3, 1, 10, 0, 0);
	private static final LocalDateTime LATER_COMPLETION = LocalDateTime.of(2026, 6, 1, 10, 0, 0);

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword(), "test.db.changelog.xml", "test");

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	private final AttemptDaoImpl sut = new AttemptDaoImpl();

	@Test
	@Order(1)
	void seed() throws SQLException {
		insertUserProfiles(USER_WITHOUT_ATTEMPT, USER_TO_START, USER_WITH_COMPLETED_ONLY, USER_TO_COMPLETE, USER_WITH_HISTORY);
	}

	@Test
	@Order(2)
	void findInProgressIdIsEmptyWhenTheUserHasNoAttempt(Mutiny.SessionFactory sessionFactory) {
		var found = factory(sessionFactory)
				.withoutTransaction(em -> sut.findInProgressId(em, USER_WITHOUT_ATTEMPT)).await().atMost(WAIT);

		assertTrue(found.isEmpty(), "no attempt started yet for this user");
	}

	@Test
	@Order(3)
	void startInProgressCreatesAnInProgressAttemptThatIsThenFound(Mutiny.SessionFactory sessionFactory) {
		var id = factory(sessionFactory).withTransaction(tx -> sut.startInProgress(tx, USER_TO_START)).await().atMost(WAIT);
		assertNotNull(id, "the new attempt's id is returned");

		var found = factory(sessionFactory)
				.withoutTransaction(em -> sut.findInProgressId(em, USER_TO_START)).await().atMost(WAIT);
		assertEquals(Optional.of(id), found, "the finder locates the just-started attempt");

		var attempt = factory(sessionFactory).withoutTransaction(em -> em.find(AttemptEntity.class, id)).await().atMost(WAIT);
		assertEquals(USER_TO_START, attempt.getUserId(), "owned by the requesting user");
		assertEquals(AttemptStatus.IN_PROGRESS, attempt.getStatus(), "started in progress");
		assertNull(attempt.getCompletedAt(), "not yet completed");
	}

	@Test
	@Order(4)
	void findInProgressIdIgnoresCompletedAttempts(Mutiny.SessionFactory sessionFactory) {
		var completed = new AttemptEntity();
		completed.setId(UUID.randomUUID());
		completed.setUserId(USER_WITH_COMPLETED_ONLY);
		completed.setStatus(AttemptStatus.COMPLETED);
		completed.setCompletedAt(LocalDateTime.now());
		factory(sessionFactory).withTransaction(tx -> tx.persist(completed)).await().atMost(WAIT);

		var found = factory(sessionFactory)
				.withoutTransaction(em -> sut.findInProgressId(em, USER_WITH_COMPLETED_ONLY)).await().atMost(WAIT);

		assertTrue(found.isEmpty(), "a completed attempt is not returned as in-progress");
	}

	@Test
	@Order(5)
	void completeFreezesTheInProgressAttemptAndStampsTheTime(Mutiny.SessionFactory sessionFactory) {
		var id = factory(sessionFactory).withTransaction(tx -> sut.startInProgress(tx, USER_TO_COMPLETE)).await().atMost(WAIT);

		factory(sessionFactory).withTransaction(tx -> sut.complete(tx, id, LATER_COMPLETION)).await().atMost(WAIT);

		var attempt = factory(sessionFactory).withoutTransaction(em -> em.find(AttemptEntity.class, id)).await().atMost(WAIT);
		assertEquals(AttemptStatus.COMPLETED, attempt.getStatus(), "the attempt is now completed");
		assertEquals(LATER_COMPLETION, attempt.getCompletedAt(), "stamped with the given completion time");

		var stillInProgress = factory(sessionFactory)
				.withoutTransaction(em -> sut.findInProgressId(em, USER_TO_COMPLETE)).await().atMost(WAIT);
		assertTrue(stillInProgress.isEmpty(), "the user no longer has an in-progress attempt");
	}

	@Test
	@Order(6)
	void findLatestCompletedAtReturnsTheMostRecentCompletion(Mutiny.SessionFactory sessionFactory) {
		factory(sessionFactory).withTransaction(tx -> tx.persistAll(
				completedAttempt(USER_WITH_HISTORY, EARLIER_COMPLETION),
				completedAttempt(USER_WITH_HISTORY, LATER_COMPLETION))).await().atMost(WAIT);

		var latest = factory(sessionFactory)
				.withoutTransaction(em -> sut.findLatestCompletedAt(em, USER_WITH_HISTORY)).await().atMost(WAIT);

		assertEquals(Optional.of(LATER_COMPLETION), latest, "the most recent of several completions");
	}

	@Test
	@Order(7)
	void findLatestCompletedAtIsEmptyWhenOnlyAnInProgressAttemptExists(Mutiny.SessionFactory sessionFactory) {
		var latest = factory(sessionFactory)
				.withoutTransaction(em -> sut.findLatestCompletedAt(em, USER_TO_START)).await().atMost(WAIT);

		assertTrue(latest.isEmpty(), "an in-progress attempt is not a completion");
	}

	private static AttemptEntity completedAttempt(UUID userId, LocalDateTime completedAt) {
		var attempt = new AttemptEntity();
		attempt.setId(UUID.randomUUID());
		attempt.setUserId(userId);
		attempt.setStatus(AttemptStatus.COMPLETED);
		attempt.setCompletedAt(completedAt);
		return attempt;
	}

	private static ReactivePersistenceContextFactoryImpl factory(Mutiny.SessionFactory sessionFactory) {
		return new ReactivePersistenceContextFactoryImpl(sessionFactory);
	}

	private static void insertUserProfiles(UUID... ids) throws SQLException {
		try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
				var s = c.prepareStatement("INSERT INTO TH_USER_PROFILE (id) VALUES (?)")) {
			for (var id : ids) {
				s.setObject(1, id);
				s.executeUpdate();
			}
		}
	}
}
