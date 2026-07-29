package eu.tealhelix.sfc.tests;

import static io.restassured.http.ContentType.JSON;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.common.services.generic.DateTimeService;
import eu.tealhelix.common.services.generic.impl.DateTimeServiceImpl;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.common.web.authentication.jwt.TokenHelper;
import eu.tealhelix.common.web.authentication.jwt.TokenHelperImpl;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The compass overview against a real (Postgres-only) database seeded from {@code appdata}, with time controlled through
 * a stubbed {@link DateTimeService}. It reports, measured against the user's current attempt: the five categories with
 * overall and per-category progress (answered/total and percentage) and estimated completion time, the localized scale
 * labels, the attempt status, and whether a fresh attempt may be started. A fresh user sees zero progress and full
 * totals; answering advances the counts; a completed attempt reads as fully answered and locked until its stability
 * window elapses, after which the overview reports the user eligible again.
 * <p>
 * The assembly branches in isolation (status/eligibility/percentage) are covered by {@code CompassReadServiceImplTest};
 * completion and the window over real time by {@code CompassCompletionTest}; the happy path end-to-end with a real token
 * by {@code CompassWorkflowTest}.
 */
@QuarkusTest
@WithCompassDb
public class CompassOverviewTest {
	private static final String BASE = "/api/howibuy/v1/sfc";
	private static final String BEARER_USER = "Bearer user";

	private static final String END_USER_ID = "2e788895-0503-4777-a7bd-24e5d61db5b1";
	private static final User END_USER = new UserImpl(new UserIdImpl(END_USER_ID), null, null, false, false);

	private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 3, 1, 10, 0, 0);

	/** The seeded item pool: how many questions each dimension's category holds, and the total. */
	private static final Map<String, Integer> QUESTIONS_PER_DIMENSION = Map.of(
			SustainabilityDimension.ECOLOGICAL.name(), 11,
			SustainabilityDimension.HEALTH.name(), 8,
			SustainabilityDimension.ANIMAL_WELFARE.name(), 11,
			SustainabilityDimension.SOCIAL.name(), 10,
			SustainabilityDimension.ECONOMIC.name(), 3);
	private static final int TOTAL_QUESTIONS = 43;

	@Inject
	DataSource dataSource;

	@ConfigProperty(name = "sfc.stability-window")
	Duration stabilityWindow;

	@ConfigProperty(name = "sfc.seconds-per-question")
	int secondsPerQuestion;

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
	void aFreshUserSeesZeroProgressFullTotalsAndIsEligibleToStart() {
		var overview = overview("en");

		assertNull(overview.get("attemptStatus"), "no attempt has been started");
		assertTrue((boolean) overview.get("eligibleToStartNewAttempt"), "a user with no attempt may start one");
		assertNull(overview.get("eligibleAt"), "there is no completed attempt to wait on");

		assertProgress(overview.get("overallProgress"), 0, TOTAL_QUESTIONS, 0);
		assertEquals((long) secondsPerQuestion * TOTAL_QUESTIONS, overallEstimate(overview), "overall estimate is seconds-per-question times every question");

		var byDimension = categoriesByDimension(overview);
		assertEquals(QUESTIONS_PER_DIMENSION.keySet(), byDimension.keySet(), "one line per seeded category");
		QUESTIONS_PER_DIMENSION.forEach((dimension, count) -> {
			var line = byDimension.get(dimension);
			assertProgress(line.get("progress"), 0, count, 0);
			assertEquals((long) secondsPerQuestion * count, estimate(line), () -> dimension + " estimate");
		});
	}

	@Test
	void overviewCountsTheAnswersOnAnInProgressAttempt() throws SQLException {
		answerAll(questionIdsForCategory(dimensionCategoryId(SustainabilityDimension.ECONOMIC)));
		var ecological = questionIdsForCategory(dimensionCategoryId(SustainabilityDimension.ECOLOGICAL));
		answerAll(ecological.subList(0, 2));

		var overview = overview("en");

		assertEquals("IN_PROGRESS", overview.get("attemptStatus"));
		assertFalse((boolean) overview.get("eligibleToStartNewAttempt"), "an attempt is already in progress");
		assertProgress(overview.get("overallProgress"), 5, TOTAL_QUESTIONS, 12);

		var byDimension = categoriesByDimension(overview);
		assertProgress(byDimension.get(SustainabilityDimension.ECONOMIC.name()).get("progress"), 3, 3, 100);
		assertProgress(byDimension.get(SustainabilityDimension.ECOLOGICAL.name()).get("progress"), 2, 11, 18);
		assertProgress(byDimension.get(SustainabilityDimension.HEALTH.name()).get("progress"), 0, 8, 0);
	}

	@Test
	void aCompletedAttemptReadsAsFullyAnsweredAndLockedWithinTheWindow() throws SQLException {
		completeEveryQuestion();

		var overview = overview("en");

		assertEquals("COMPLETED", overview.get("attemptStatus"));
		assertProgress(overview.get("overallProgress"), TOTAL_QUESTIONS, TOTAL_QUESTIONS, 100);
		categoriesByDimension(overview).forEach((dimension, line) ->
				assertProgress(line.get("progress"), QUESTIONS_PER_DIMENSION.get(dimension), QUESTIONS_PER_DIMENSION.get(dimension), 100));
		assertFalse((boolean) overview.get("eligibleToStartNewAttempt"), "still inside the stability window");
		assertEquals(COMPLETED_AT.plus(stabilityWindow), LocalDateTime.parse((String) overview.get("eligibleAt")), "told when a re-take becomes possible");
	}

	@Test
	void aCompletedAttemptBecomesEligibleOnceTheWindowElapses() throws SQLException {
		completeEveryQuestion();

		clock.set(COMPLETED_AT.plus(stabilityWindow).plusDays(1));
		var overview = overview("en");

		assertEquals("COMPLETED", overview.get("attemptStatus"), "the completed attempt remains the record");
		assertProgress(overview.get("overallProgress"), TOTAL_QUESTIONS, TOTAL_QUESTIONS, 100);
		assertTrue((boolean) overview.get("eligibleToStartNewAttempt"), "the stability window has elapsed");
	}

	@Test
	void servesTheScaleLabelsLocalized() {
		var english = overview("en");
		assertEquals("Not important", labels(english).get("NOT_IMPORTANT"), "English labels");
		assertEquals(5, labels(english).size(), "all five scale points");

		var greek = overview("el");
		assertEquals("Καθόλου σημαντικό", labels(greek).get("NOT_IMPORTANT"), "labels localized to Greek");
		assertEquals("Εξαιρετικά σημαντικό", labels(greek).get("EXTREMELY_IMPORTANT"), "labels localized to Greek");
	}

	@Test
	void rejectsAnUnsupportedLanguageWith400() {
		RestAssured.given()
				.header("Authorization", BEARER_USER)
				.queryParam("lang", "fr")
				.when().get(BASE + "/overview")
				.then().statusCode(400);
	}

	private void completeEveryQuestion() throws SQLException {
		answerAll(allQuestionIds());
		RestAssured.given()
				.header("Authorization", BEARER_USER)
				.when().post(BASE + "/attempts/current/completion")
				.then().statusCode(204);
	}

	private void answerAll(List<String> questionIds) {
		for (var questionId : questionIds) {
			RestAssured.given()
					.header("Authorization", BEARER_USER)
					.contentType(JSON)
					.body("{\"option\":\"MODERATELY_IMPORTANT\"}")
					.when().put(BASE + "/questions/" + questionId + "/answer")
					.then().statusCode(204);
		}
	}

	private Map<String, Object> overview(String language) {
		return RestAssured.given()
				.header("Authorization", BEARER_USER)
				.queryParam("lang", language)
				.when().get(BASE + "/overview")
				.then().statusCode(200).contentType(JSON)
				.extract().jsonPath().getMap("$");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String> labels(Map<String, Object> overview) {
		return (Map<String, String>) overview.get("scaleLabels");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Map<String, Object>> categoriesByDimension(Map<String, Object> overview) {
		var categories = (List<Map<String, Object>>) overview.get("categories");
		return categories.stream().collect(toMap(line -> (String) ((Map<String, Object>) line.get("category")).get("dimension"), identity()));
	}

	private static void assertProgress(Object progress, int answered, int total, int percentage) {
		@SuppressWarnings("unchecked")
		var p = (Map<String, Object>) progress;
		assertEquals(answered, p.get("answered"), "answered");
		assertEquals(total, p.get("total"), "total");
		assertEquals(percentage, p.get("percentage"), "percentage");
	}

	private static long overallEstimate(Map<String, Object> overview) {
		return ((Number) overview.get("overallEstimatedSeconds")).longValue();
	}

	private static long estimate(Map<String, Object> categoryLine) {
		return ((Number) categoryLine.get("estimatedSeconds")).longValue();
	}

	private String dimensionCategoryId(SustainabilityDimension dimension) throws SQLException {
		return scalarString("SELECT id FROM TH_SFC_CATEGORY WHERE dimension = '" + dimension.name() + "'");
	}

	private List<String> questionIdsForCategory(String categoryId) throws SQLException {
		return idList("SELECT id FROM TH_SFC_QUESTION WHERE category_id = '" + categoryId + "' ORDER BY position");
	}

	private List<String> allQuestionIds() throws SQLException {
		return idList("SELECT id FROM TH_SFC_QUESTION ORDER BY id");
	}

	private List<String> idList(String sql) throws SQLException {
		var ids = new ArrayList<String>();
		try (var c = dataSource.getConnection(); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			while (rs.next()) {
				ids.add(rs.getString("id"));
			}
		}
		return ids;
	}

	private String scalarString(String sql) throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
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

	/** A clock whose "now" the test sets, so stability-window eligibility is exercised without real time passing. */
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
