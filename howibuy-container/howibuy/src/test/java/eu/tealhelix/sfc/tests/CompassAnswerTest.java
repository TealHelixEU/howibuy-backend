package eu.tealhelix.sfc.tests;

import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.common.test.quarkus.PostgresTestResource;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.common.web.authentication.jwt.TokenHelper;
import eu.tealhelix.common.web.authentication.jwt.TokenHelperImpl;
import eu.tealhelix.sfc.v1.types.ScaleOption;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The save path against a real database: answering through the JAX-RS {@code PUT} lazily starts the user's attempt,
 * persists immediately, and re-answering overwrites in place while answers accumulate on the same attempt across
 * separate requests (pause/resume). Also pins the two database-level guards the schema promises — the 1–5 value check
 * and at-most-one-in-progress-attempt-per-user. State is asserted directly over JDBC because there is no answer-read
 * endpoint yet. JWT decoding is faked (a stubbed {@link TokenHelper}); everything else runs for real.
 * <p>
 * The service's authorization and lazy-vs-reuse branching is unit-covered by {@code CompassAttemptServiceImplTest};
 * the anonymous {@code 401} by {@code CompassAccessControlTest}.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresTestResource.class, initArgs = {
		@ResourceArg(name = "contexts", value = "appdata")
})
public class CompassAnswerTest {
	private static final String BASE = "/api/howibuy/v1/sfc";
	private static final String BEARER_USER = "Bearer user";
	private static final String BEARER_SERVICE = "Bearer service";

	private static final String END_USER_ID = "2e788895-0503-4777-a7bd-24e5d61db5b1";
	private static final UUID END_USER_UUID = UUID.fromString(END_USER_ID);
	private static final User END_USER = new UserImpl(new UserIdImpl(END_USER_ID), null, null, false, false);
	private static final User SERVICE_USER = new UserImpl(new UserIdImpl(END_USER_ID), null, null, false, true);

	@Inject
	DataSource dataSource;

	@BeforeEach
	void setUp() throws SQLException {
		QuarkusMock.installMockForType(new StubTokenHelper(), TokenHelper.class);
		try (var c = connect(); var s = c.createStatement()) {
			s.execute("DELETE FROM TH_SFC_ANSWER");
			s.execute("DELETE FROM TH_SFC_ATTEMPT");
			s.execute("INSERT INTO TH_USER_PROFILE (id) VALUES ('" + END_USER_ID + "') ON CONFLICT (id) DO NOTHING");
		}
	}

	@Test
	void savesAnAnswerImmediatelyAndStartsAnAttempt() throws SQLException {
		var questionId = questionIds(1).get(0);

		putAnswer(questionId, ScaleOption.VERY_IMPORTANT, BEARER_USER, 204);

		assertEquals(1, inProgressAttemptCount(), "the first answer lazily started one in-progress attempt");
		assertEquals(1, answerCount(), "exactly one answer saved");
		assertEquals(ScaleOption.VERY_IMPORTANT.getValue(), answerValue(questionId), "the chosen option's ordinal was persisted");
		assertEquals(attemptId(), answerAttemptId(questionId), "the answer hangs off the started attempt");
	}

	@Test
	void reAnsweringOverwritesTheValueInPlace() throws SQLException {
		var questionId = questionIds(1).get(0);

		putAnswer(questionId, ScaleOption.SLIGHTLY_IMPORTANT, BEARER_USER, 204);
		putAnswer(questionId, ScaleOption.EXTREMELY_IMPORTANT, BEARER_USER, 204);

		assertEquals(1, answerCount(), "re-answering overwrote rather than adding a row");
		assertEquals(ScaleOption.EXTREMELY_IMPORTANT.getValue(), answerValue(questionId), "the latest choice won");
		assertEquals(1, inProgressAttemptCount(), "still a single in-progress attempt");
	}

	@Test
	void answersAccumulateOnTheSameAttemptAcrossRequests() throws SQLException {
		var questions = questionIds(2);

		putAnswer(questions.get(0), ScaleOption.NOT_IMPORTANT, BEARER_USER, 204);
		putAnswer(questions.get(1), ScaleOption.EXTREMELY_IMPORTANT, BEARER_USER, 204);

		assertEquals(1, inProgressAttemptCount(), "both answers landed on one attempt (resume, not restart)");
		assertEquals(2, answerCount(), "both answers are kept");
		assertEquals(answerAttemptId(questions.get(0)), answerAttemptId(questions.get(1)), "both hang off the same attempt");
	}

	@Test
	void rejectsAServiceAccountWith403() throws SQLException {
		var questionId = questionIds(1).get(0);

		putAnswer(questionId, ScaleOption.VERY_IMPORTANT, BEARER_SERVICE, 403);

		assertEquals(0, attemptCount(), "a rejected caller starts no attempt");
	}

	@Test
	void databaseRejectsAnOutOfRangeValue() throws SQLException {
		var questionId = questionIds(1).get(0);
		var attemptId = UUID.randomUUID();
		try (var c = connect()) {
			insertAttempt(c, attemptId, END_USER_UUID, "IN_PROGRESS");
			var e = assertThrows(SQLException.class, () -> {
				try (var s = c.prepareStatement("INSERT INTO TH_SFC_ANSWER (attempt_id, question_id, value) VALUES (?, ?, 6)")) {
					s.setObject(1, attemptId);
					s.setObject(2, questionId);
					s.executeUpdate();
				}
			});
			assertTrue(e.getMessage().toLowerCase().contains("ck_th_sfc_answer__value_range"), () -> "the 1–5 check rejected it: " + e.getMessage());
		}
	}

	@Test
	void databaseAllowsAtMostOneInProgressAttemptPerUser() throws SQLException {
		try (var c = connect()) {
			insertAttempt(c, UUID.randomUUID(), END_USER_UUID, "IN_PROGRESS");

			var e = assertThrows(SQLException.class, () -> insertAttempt(c, UUID.randomUUID(), END_USER_UUID, "IN_PROGRESS"));
			assertTrue(e.getMessage().toLowerCase().contains("uq_th_sfc_attempt__one_in_progress"), () -> "the partial unique index rejected a second in-progress attempt: " + e.getMessage());

			insertAttempt(c, UUID.randomUUID(), END_USER_UUID, "COMPLETED");
			assertEquals(1, inProgressAttemptCount(), "completed attempts are unconstrained; only in-progress is unique");
		}
	}

	private void putAnswer(UUID questionId, ScaleOption option, String bearer, int expectedStatus) {
		RestAssured.given()
				.header("Authorization", bearer)
				.contentType(JSON)
				.body("{\"option\":\"" + option.name() + "\"}")
				.when()
				.put(BASE + "/questions/" + questionId + "/answer")
				.then()
				.statusCode(expectedStatus);
	}

	private Connection connect() throws SQLException {
		return dataSource.getConnection();
	}

	private void insertAttempt(Connection c, UUID id, UUID userId, String status) throws SQLException {
		try (var s = c.prepareStatement("INSERT INTO TH_SFC_ATTEMPT (id, user_id, status) VALUES (?, ?, ?)")) {
			s.setObject(1, id);
			s.setObject(2, userId);
			s.setString(3, status);
			s.executeUpdate();
		}
	}

	private List<UUID> questionIds(int n) throws SQLException {
		var ids = new ArrayList<UUID>();
		try (var c = connect(); var s = c.createStatement(); var rs = s.executeQuery("SELECT id FROM TH_SFC_QUESTION ORDER BY id LIMIT " + n)) {
			while (rs.next()) {
				ids.add(UUID.fromString(rs.getString("id")));
			}
		}
		return ids;
	}

	private long attemptCount() throws SQLException {
		return scalar("SELECT count(*) FROM TH_SFC_ATTEMPT WHERE user_id = '" + END_USER_ID + "'");
	}

	private long inProgressAttemptCount() throws SQLException {
		return scalar("SELECT count(*) FROM TH_SFC_ATTEMPT WHERE user_id = '" + END_USER_ID + "' AND status = 'IN_PROGRESS'");
	}

	private long answerCount() throws SQLException {
		return scalar("SELECT count(*) FROM TH_SFC_ANSWER");
	}

	private short answerValue(UUID questionId) throws SQLException {
		return (short) scalar("SELECT value FROM TH_SFC_ANSWER WHERE question_id = '" + questionId + "'");
	}

	private UUID attemptId() throws SQLException {
		try (var c = connect(); var s = c.createStatement(); var rs = s.executeQuery("SELECT id FROM TH_SFC_ATTEMPT WHERE user_id = '" + END_USER_ID + "'")) {
			rs.next();
			return UUID.fromString(rs.getString("id"));
		}
	}

	private UUID answerAttemptId(UUID questionId) throws SQLException {
		try (var c = connect(); var s = c.createStatement(); var rs = s.executeQuery("SELECT attempt_id FROM TH_SFC_ANSWER WHERE question_id = '" + questionId + "'")) {
			rs.next();
			return UUID.fromString(rs.getString("attempt_id"));
		}
	}

	private long scalar(String sql) throws SQLException {
		try (var c = connect(); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}

	/** Fakes JWT decoding: the token {@code "service"} becomes a service account, anything else the end-user. */
	private static final class StubTokenHelper extends TokenHelperImpl {
		StubTokenHelper() {
			super(null, null, null, null, null);
		}

		@Override
		public Uni<User> processToken(String token) {
			return Uni.createFrom().item("service".equals(token) ? SERVICE_USER : END_USER);
		}

		@Override
		public User makeUnauthenticated() {
			return new UserImpl(null, null, null, false, false);
		}
	}
}
