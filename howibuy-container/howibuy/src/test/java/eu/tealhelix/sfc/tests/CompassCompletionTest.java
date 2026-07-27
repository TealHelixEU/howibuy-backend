package eu.tealhelix.sfc.tests;

import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.services.generic.impl.DateTimeServiceImpl;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.common.web.authentication.jwt.TokenHelper;
import eu.tealhelix.common.web.authentication.jwt.TokenHelperImpl;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Completion, locking, the stability window and re-take against a real (Postgres-only) database seeded from
 * {@code appdata}, with time controlled through a stubbed {@link DateTimeService} so the window is exercised without
 * sleeping. Completion is refused (422, with the unanswered ids) until every question is answered; once locked the
 * attempt becomes an immutable {@code COMPLETED} record; a further answer inside the window is refused (409) and leaves
 * that record untouched; and once the window has elapsed the next answer starts a fresh, blank attempt while the
 * previous one remains. Answers and completion go through the real endpoints; JWT decoding and the clock are the only
 * fakes.
 * <p>
 * The frontier/pairing reads are covered by {@code CompassNavigationTest}; the immediate-save path by
 * {@code CompassAnswerTest}; the completion orchestration and the window comparison in isolation by
 * {@code CompassAttemptServiceImplTest} and {@code StabilityWindowTest}.
 */
@QuarkusTest
@WithCompassDb
public class CompassCompletionTest {
	private static final String BASE = "/api/howibuy/v1/sfc";
	private static final String BEARER_USER = "Bearer user";

	private static final String END_USER_ID = "2e788895-0503-4777-a7bd-24e5d61db5b1";
	private static final User END_USER = new UserImpl(new UserIdImpl(END_USER_ID), null, null, false, false);

	private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 3, 1, 10, 0, 0);

	@Inject
	DataSource dataSource;

	@ConfigProperty(name = "sfc.stability-window")
	Duration stabilityWindow;

	private final MutableClock clock = new MutableClock();

	@BeforeEach
	void setUp() throws SQLException {
		QuarkusMock.installMockForType(new StubTokenHelper(), TokenHelper.class);
		clock.set(COMPLETED_AT);
		QuarkusMock.installMockForType(clock, DateTimeService.class);
		try (var c = dataSource.getConnection(); var s = c.createStatement()) {
			s.execute("DELETE FROM TH_SFC_ANSWER");
			s.execute("DELETE FROM TH_SFC_ATTEMPT");
			s.execute("INSERT INTO TH_USER_PROFILE (id) VALUES ('" + END_USER_ID + "') ON CONFLICT (id) DO NOTHING");
		}
	}

	@Test
	void completionIsRefusedWith422ListingTheUnansweredQuestionsWhileTheAttemptStaysOpen() throws SQLException {
		var questions = allQuestionIds();
		putAnswer(questions.getFirst(), "VERY_IMPORTANT");

		var unanswered = complete(422).extract().jsonPath().getList("unansweredQuestionIds", String.class);

		assertEquals(questions.size() - 1, unanswered.size(), "every question but the one answered is reported unanswered");
		assertFalse(unanswered.contains(questions.getFirst()), "the answered question is not listed");
		assertEquals(1, inProgressAttemptCount(), "the attempt is not locked — it stays in progress");
		assertEquals(0, completedAttemptCount(), "nothing was completed");
	}

	@Test
	void completingAFullyAnsweredCompassLocksItAndStampsTheTime() throws SQLException {
		answerEveryQuestion();

		complete(204);

		assertEquals(1, completedAttemptCount(), "the attempt is now a completed record");
		assertEquals(0, inProgressAttemptCount(), "nothing remains in progress");
		assertEquals(COMPLETED_AT, completedAtOfCompletedAttempt(), "stamped with the controlled completion time");
	}

	@Test
	void withinTheStabilityWindowAFurtherAnswerIsRefusedAndTheRecordIsUnchanged() throws SQLException {
		var questions = allQuestionIds();
		answerEveryQuestion();
		complete(204);
		var answersInRecord = answerCount();

		clock.set(COMPLETED_AT.plus(stabilityWindow.dividedBy(2)));
		var eligibleAt = putAnswerExpecting(questions.getFirst(), "NOT_IMPORTANT", 409).extract().path("eligibleAt");

		assertNotNull(eligibleAt, "the user is told when a new attempt becomes possible");
		assertEquals(1, attemptCount(), "no new attempt was started");
		assertEquals(1, completedAttemptCount(), "the completed record is still the only attempt");
		assertEquals(answersInRecord, answerCount(), "the completed attempt's answers are untouched — it is immutable");
	}

	@Test
	void onceTheWindowHasElapsedTheNextAnswerStartsAFreshBlankAttempt() throws SQLException {
		var questions = allQuestionIds();
		answerEveryQuestion();
		complete(204);
		var answersInRecord = answerCount();

		clock.set(COMPLETED_AT.plus(stabilityWindow).plusDays(1));
		putAnswer(questions.getFirst(), "SLIGHTLY_IMPORTANT");

		assertEquals(2, attemptCount(), "the previous completed attempt plus a new one");
		assertEquals(1, inProgressAttemptCount(), "the fresh attempt is in progress");
		assertEquals(1, completedAttemptCount(), "the previous completed attempt remains as history");
		assertEquals(1, answerCountForStatus("IN_PROGRESS"), "the fresh attempt starts blank — only the just-given answer");
		assertEquals(answersInRecord, answerCountForStatus("COMPLETED"), "the previous record carried nothing forward and lost nothing");
	}

	private void answerEveryQuestion() throws SQLException {
		for (var questionId : allQuestionIds()) {
			putAnswer(questionId, "MODERATELY_IMPORTANT");
		}
	}

	private void putAnswer(String questionId, String option) {
		putAnswerExpecting(questionId, option, 204);
	}

	private ValidatableResponse putAnswerExpecting(String questionId, String option, int status) {
		return RestAssured.given()
				.header("Authorization", BEARER_USER)
				.contentType(JSON)
				.body("{\"option\":\"" + option + "\"}")
				.when().put(BASE + "/questions/" + questionId + "/answer")
				.then().statusCode(status);
	}

	private ValidatableResponse complete(int status) {
		return RestAssured.given()
				.header("Authorization", BEARER_USER)
				.when().post(BASE + "/attempts/current/completion")
				.then().statusCode(status);
	}

	private List<String> allQuestionIds() throws SQLException {
		var ids = new ArrayList<String>();
		try (var c = dataSource.getConnection(); var s = c.createStatement();
				var rs = s.executeQuery("SELECT id FROM TH_SFC_QUESTION ORDER BY id")) {
			while (rs.next()) {
				ids.add(rs.getString("id"));
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

	private long completedAttemptCount() throws SQLException {
		return scalar("SELECT count(*) FROM TH_SFC_ATTEMPT WHERE user_id = '" + END_USER_ID + "' AND status = 'COMPLETED'");
	}

	private long answerCount() throws SQLException {
		return scalar("SELECT count(*) FROM TH_SFC_ANSWER");
	}

	private long answerCountForStatus(String status) throws SQLException {
		return scalar("SELECT count(*) FROM TH_SFC_ANSWER a JOIN TH_SFC_ATTEMPT t ON a.attempt_id = t.id"
				+ " WHERE t.user_id = '" + END_USER_ID + "' AND t.status = '" + status + "'");
	}

	private LocalDateTime completedAtOfCompletedAttempt() throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement();
				var rs = s.executeQuery("SELECT completed_at FROM TH_SFC_ATTEMPT WHERE user_id = '" + END_USER_ID + "' AND status = 'COMPLETED'")) {
			assertTrue(rs.next(), "a completed attempt exists");
			return rs.getTimestamp("completed_at").toLocalDateTime();
		}
	}

	private long scalar(String sql) throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}

	/** Fakes JWT decoding so any bearer token resolves to the end-user. */
	private static final class StubTokenHelper extends TokenHelperImpl {
		StubTokenHelper() {
			super(null, null, null, null, null);
		}

		@Override
		public Uni<User> processToken(String token) {
			return Uni.createFrom().item(END_USER);
		}
	}

	/** A clock whose "now" the test sets, so the stability window is exercised without real time passing. */
	private static final class MutableClock extends DateTimeServiceImpl {
		private volatile LocalDateTime now;

		void set(LocalDateTime now) {
			this.now = now;
		}

		@Override
		public LocalDateTime getNow() {
			return now;
		}

		@Override
		public long currentTimeMillis() {
			return now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		}
	}
}
