package eu.tealhelix.sfc.tests;

import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.common.web.authentication.jwt.TokenHelper;
import eu.tealhelix.common.web.authentication.jwt.TokenHelperImpl;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Navigation and review against a real (Postgres-only) database seeded from {@code appdata}: asking for a category's
 * next question hands back the lowest-position unanswered question and advances as answers are given; a fully-answered
 * category signals complete and never yields a question from another category; reviewing a category — or the whole
 * compass — pairs each question with the user's current answer (or none); and changing an earlier answer leaves the
 * frontier at the earliest remaining unanswered question. Answers are made through the real {@code PUT} endpoint; JWT
 * decoding is the only part faked (a stubbed {@link TokenHelper}), so the filter, authorization, resource, DAOs and JSON
 * mapping all run for real.
 * <p>
 * The frontier and pairing logic in isolation is unit-covered by {@code CompassReadServiceImplTest}; the answer-save
 * path by {@code CompassAnswerTest}; the localized structure reads by {@code CompassStructureTest}.
 */
@QuarkusTest
@WithCompassDb
public class CompassNavigationTest {
	private static final String BASE = "/api/howibuy/v1/sfc";
	private static final String BEARER_USER = "Bearer user";

	private static final String END_USER_ID = "2e788895-0503-4777-a7bd-24e5d61db5b1";
	private static final User END_USER = new UserImpl(new UserIdImpl(END_USER_ID), null, null, false, false);

	private static final TypeRef<List<Map<String, Object>>> LIST_OF_OBJECTS = new TypeRef<>() {};
	private static final TypeRef<Map<String, Object>> OBJECT = new TypeRef<>() {};

	@Inject
	DataSource dataSource;

	@BeforeEach
	void setUp() throws SQLException {
		QuarkusMock.installMockForType(new StubTokenHelper(), TokenHelper.class);
		try (var c = dataSource.getConnection(); var s = c.createStatement()) {
			s.execute("DELETE FROM TH_SFC_ANSWER");
			s.execute("DELETE FROM TH_SFC_ATTEMPT");
			s.execute("INSERT INTO TH_USER_PROFILE (id) VALUES ('" + END_USER_ID + "') ON CONFLICT (id) DO NOTHING");
		}
	}

	@Test
	void nextQuestionStartsAtTheFirstQuestionAndAdvancesAsAnswersAreGiven() {
		var economic = categoryId("ECONOMIC");
		var questions = questionIds(economic);

		var next = nextQuestion(economic);
		assertFalse((Boolean) next.get("complete"), "an untouched category is not complete");
		assertEquals(questions.get(0), questionOf(next).get("id"), "the frontier starts at the first question");

		putAnswer(questions.get(0), "VERY_IMPORTANT");

		assertEquals(questions.get(1), questionOf(nextQuestion(economic)).get("id"), "answering the first advances the frontier to the second");
	}

	@Test
	void answeringEveryQuestionInACategorySignalsCompleteWithoutCrossingCategories() {
		var economic = categoryId("ECONOMIC");
		questionIds(economic).forEach(question -> putAnswer(question, "MODERATELY_IMPORTANT"));

		var next = nextQuestion(economic);

		assertTrue((Boolean) next.get("complete"), "a fully-answered category is complete");
		assertNull(next.get("question"), "no question is handed back — navigation never crosses into another category");
	}

	@Test
	void reviewingACategoryPairsEachQuestionWithTheCurrentAnswerOrNone() {
		var economic = categoryId("ECONOMIC");
		var questions = questionIds(economic);
		putAnswer(questions.get(0), "EXTREMELY_IMPORTANT");

		var review = reviewCategory(economic);

		assertEquals("EXTREMELY_IMPORTANT", review.get(0).get("answer"), "the answered question carries its option");
		assertNull(review.get(1).get("answer"), "an unanswered question carries no answer");
	}

	@Test
	void reviewingAllQuestionsPairsAnswersAcrossCategories() {
		var economic = categoryId("ECONOMIC");
		var question = questionIds(economic).get(0);
		putAnswer(question, "SLIGHTLY_IMPORTANT");

		var groups = reviewAll();

		var answer = groups.stream()
				.flatMap(group -> questionsOf(group).stream())
				.filter(q -> question.equals(q.get("id")))
				.findFirst().orElseThrow()
				.get("answer");
		assertEquals("SLIGHTLY_IMPORTANT", answer, "the answered question carries its option in the all-questions review");
		var total = groups.stream().mapToInt(group -> questionsOf(group).size()).sum();
		assertEquals(43, total, "the whole item pool is still returned");
	}

	@Test
	void changingAnEarlierAnswerLeavesTheFrontierAtTheEarliestRemaining() {
		var economic = categoryId("ECONOMIC");
		var questions = questionIds(economic);
		putAnswer(questions.get(0), "NOT_IMPORTANT");
		putAnswer(questions.get(1), "VERY_IMPORTANT");
		assertEquals(questions.get(2), questionOf(nextQuestion(economic)).get("id"), "with the first two answered the frontier is the third");

		putAnswer(questions.get(0), "EXTREMELY_IMPORTANT");

		assertEquals(questions.get(2), questionOf(nextQuestion(economic)).get("id"), "changing an earlier answer does not move the frontier off the earliest remaining");
		assertEquals("EXTREMELY_IMPORTANT", reviewCategory(economic).get(0).get("answer"), "the changed answer stuck");
	}

	private String categoryId(String dimension) {
		return getCategories().stream()
				.filter(category -> dimension.equals(category.get("dimension")))
				.map(category -> (String) category.get("id"))
				.findFirst().orElseThrow();
	}

	private List<Map<String, Object>> getCategories() {
		return RestAssured.given().header("Authorization", BEARER_USER)
				.when().get(BASE + "/categories")
				.then().statusCode(200).contentType(JSON)
				.extract().as(LIST_OF_OBJECTS);
	}

	private List<String> questionIds(String categoryId) {
		return reviewCategory(categoryId).stream().map(question -> (String) question.get("id")).toList();
	}

	private List<Map<String, Object>> reviewCategory(String categoryId) {
		return RestAssured.given().header("Authorization", BEARER_USER)
				.when().get(BASE + "/categories/" + categoryId + "/questions")
				.then().statusCode(200).contentType(JSON)
				.extract().as(LIST_OF_OBJECTS);
	}

	private List<Map<String, Object>> reviewAll() {
		return RestAssured.given().header("Authorization", BEARER_USER)
				.when().get(BASE + "/questions")
				.then().statusCode(200).contentType(JSON)
				.extract().as(LIST_OF_OBJECTS);
	}

	private Map<String, Object> nextQuestion(String categoryId) {
		return RestAssured.given().header("Authorization", BEARER_USER)
				.when().get(BASE + "/categories/" + categoryId + "/next-question")
				.then().statusCode(200).contentType(JSON)
				.extract().as(OBJECT);
	}

	private void putAnswer(String questionId, String option) {
		RestAssured.given()
				.header("Authorization", BEARER_USER)
				.contentType(JSON)
				.body("{\"option\":\"" + option + "\"}")
				.when().put(BASE + "/questions/" + questionId + "/answer")
				.then().statusCode(204);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> questionOf(Map<String, Object> next) {
		return (Map<String, Object>) next.get("question");
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> questionsOf(Map<String, Object> group) {
		return (List<Map<String, Object>>) group.get("questions");
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

		@Override
		public User makeUnauthenticated() {
			return new UserImpl(null, null, null, false, false);
		}
	}
}
