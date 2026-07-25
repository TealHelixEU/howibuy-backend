package eu.tealhelix.sfc.tests;

import static io.restassured.http.ContentType.JSON;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import eu.tealhelix.common.web.authentication.jwt.TokenHelper;
import eu.tealhelix.common.web.authentication.jwt.TokenHelperImpl;
import eu.tealhelix.sfc.v1.types.SustainabilityDimension;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage of reading the compass structure through the JAX-RS layer: the endpoints route, the responses
 * carry the seed content localized for the requested language, the all-questions read is grouped per category, and the
 * authorization outcomes surface as the right HTTP status. The database is the real (Postgres-only) one seeded from the
 * {@code appdata} Liquibase context. JWT decoding is the only part faked — a stubbed {@link TokenHelper} turns the
 * bearer token into a user — so the filter, authorization, resource, DAOs and JSON mapping all run for real.
 * <p>
 * The anonymous {@code 401} path is covered by {@code CompassAccessControlTest}; the orchestration logic (authorization
 * and language resolution) by {@code CompassReadServiceImplTest}; the localized joins by the DAO tests.
 */
@QuarkusTest
@WithCompassDb
public class CompassStructureTest {
	private static final String BASE = "/api/howibuy/v1/sfc";

	/** Any non-service bearer token resolves to this user; {@code "service"} resolves to {@link #SERVICE_USER}. */
	private static final String BEARER_USER = "Bearer user";
	private static final String BEARER_SERVICE = "Bearer service";

	private static final User END_USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, false);
	private static final User SERVICE_USER = new UserImpl(new UserIdImpl("2e788895-0503-4777-a7bd-24e5d61db5b1"), null, null, false, true);

	private static final TypeRef<List<Map<String, Object>>> LIST_OF_OBJECTS = new TypeRef<>() {};

	/** The number of items the official item pool defines for each dimension. */
	private static final Map<SustainabilityDimension, Integer> QUESTIONS_PER_DIMENSION = Map.of(
			SustainabilityDimension.ECOLOGICAL, 11,
			SustainabilityDimension.HEALTH, 8,
			SustainabilityDimension.ANIMAL_WELFARE, 11,
			SustainabilityDimension.SOCIAL, 10,
			SustainabilityDimension.ECONOMIC, 3);

	private static final int TOTAL_QUESTIONS = 43;

	@BeforeEach
	void stubTokenProcessing() {
		QuarkusMock.installMockForType(new StubTokenHelper(), TokenHelper.class);
	}

	@Test
	void readsAllCategoriesLocalizedInEnglish() {
		var byDimension = categoriesByDimension("en");
		assertEquals(5, byDimension.size(), "one category per dimension");

		var health = byDimension.get(SustainabilityDimension.HEALTH.name());
		assertEquals("Health", health.get("name"), "HEALTH name in English");
		assertTrue(((String) health.get("description")).contains("health"), "HEALTH description is the English one");
		assertNull(health.get("videoUrl"), "no video link has been authored yet");
		assertNull(health.get("detailUrl"), "no detail link has been authored yet");
	}

	@Test
	void readsAllCategoriesLocalizedInGreek() {
		var byDimension = categoriesByDimension("el");
		assertEquals(5, byDimension.size(), "one category per dimension");

		var health = byDimension.get(SustainabilityDimension.HEALTH.name());
		assertEquals("Υγεία", health.get("name"), "HEALTH name in Greek");
		assertTrue(((String) health.get("description")).contains("υγεία"), "HEALTH description is the Greek one");

		var ecological = byDimension.get(SustainabilityDimension.ECOLOGICAL.name());
		assertEquals("Οικολογική βιωσιμότητα", ecological.get("name"), "ECOLOGICAL name in Greek");
		assertTrue(((String) ecological.get("description")).contains("βιοποικιλότητα"), "ECOLOGICAL description parsed intact through the comma-quoted CSV");
	}

	@Test
	void defaultsToConfiguredLanguageWhenLanguageOmitted() {
		var omitted = categoriesByDimension(null);
		assertEquals("Health", omitted.get(SustainabilityDimension.HEALTH.name()).get("name"), "omitting ?lang serves the configured default (en)");
	}

	@Test
	void rejectsAnUnsupportedLanguageWith400() {
		RestAssured.given()
				.header("Authorization", BEARER_USER)
				.queryParam("lang", "fr")
				.when()
				.get(BASE + "/categories")
				.then()
				.statusCode(400);
	}

	@Test
	void readsACategorysQuestionsLocalizedInPositionOrder() {
		var ecologicalId = (String) categoriesByDimension("en").get(SustainabilityDimension.ECOLOGICAL.name()).get("id");

		var questions = categoryQuestions(ecologicalId, "en");

		var positions = questions.stream().map(q -> (Integer) q.get("position")).toList();
		assertEquals(IntStream.rangeClosed(1, 11).boxed().toList(), positions, "ECOLOGICAL's eleven items in 1-based position order");
		assertEquals(
				"Use of fossil fuels: The amount of fossil fuels (such as coal, oil, and natural gas) that is required for food production.",
				questions.get(0).get("text"),
				"first item is the localized name and description joined by a colon");
	}

	@Test
	void readsAllQuestionsAcrossCategoriesGroupedAndLocalized() {
		var groups = allQuestions("el");

		assertEquals(5, groups.size(), "one group per category — questions span all five categories");
		var texts = groups.stream()
				.flatMap(group -> questionsOf(group).stream())
				.map(q -> (String) q.get("text"))
				.toList();
		assertEquals(TOTAL_QUESTIONS, texts.size(), "the whole item pool");
		assertTrue(texts.stream().anyMatch(t -> t.contains("άνθρακα")), "prompts are the Greek ones");
	}

	@Test
	void eachCategoryHoldsItsItemPoolCount() {
		var byDimension = categoriesByDimension("en");
		QUESTIONS_PER_DIMENSION.forEach((dimension, expected) -> {
			var id = (String) byDimension.get(dimension.name()).get("id");
			var questions = categoryQuestions(id, "en");
			assertEquals((int) expected, questions.size(), () -> dimension + " item count");
		});
	}

	@Test
	void readsTheStructureInEachAdditionalLanguage() {
		var englishNames = getCategories("en").stream().map(c -> (String) c.get("name")).collect(toSet());
		for (var language : List.of("nl", "et", "de")) {
			var categories = getCategories(language);
			assertEquals(5, categories.size(), () -> "one category per dimension in " + language);
			categories.forEach(c -> assertFalse(((String) c.get("name")).isBlank(), () -> "category name present in " + language));
			var names = categories.stream().map(c -> (String) c.get("name")).collect(toSet());
			assertNotEquals(englishNames, names, () -> "category names are localized in " + language);

			var total = allQuestions(language).stream().mapToInt(group -> questionsOf(group).size()).sum();
			assertEquals(TOTAL_QUESTIONS, total, () -> "the whole item pool in " + language);
		}
	}

	@Test
	void rejectsAServiceAccountWith403() {
		RestAssured.given()
				.header("Authorization", BEARER_SERVICE)
				.when()
				.get(BASE + "/categories")
				.then()
				.statusCode(403);
	}

	private static Map<String, Map<String, Object>> categoriesByDimension(String language) {
		return getCategories(language).stream().collect(toMap(c -> (String) c.get("dimension"), identity()));
	}

	private static List<Map<String, Object>> getCategories(String language) {
		var request = RestAssured.given().header("Authorization", BEARER_USER);
		if (language != null) {
			request = request.queryParam("lang", language);
		}
		return request.when()
				.get(BASE + "/categories")
				.then()
				.statusCode(200)
				.contentType(JSON)
				.extract()
				.as(LIST_OF_OBJECTS);
	}

	private static List<Map<String, Object>> categoryQuestions(String categoryId, String language) {
		return RestAssured.given()
				.header("Authorization", BEARER_USER)
				.queryParam("lang", language)
				.when()
				.get(BASE + "/categories/" + categoryId + "/questions")
				.then()
				.statusCode(200)
				.contentType(JSON)
				.extract()
				.as(LIST_OF_OBJECTS);
	}

	private static List<Map<String, Object>> allQuestions(String language) {
		return RestAssured.given()
				.header("Authorization", BEARER_USER)
				.queryParam("lang", language)
				.when()
				.get(BASE + "/questions")
				.then()
				.statusCode(200)
				.contentType(JSON)
				.extract()
				.as(LIST_OF_OBJECTS);
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> questionsOf(Map<String, Object> group) {
		return (List<Map<String, Object>>) group.get("questions");
	}

	/**
	 * Fakes JWT decoding so an authenticated user reaches the endpoints without a real Keycloak token: the token
	 * {@code "service"} becomes a service account, anything else an end-user.
	 */
	private static final class StubTokenHelper extends TokenHelperImpl {
		/** QuarkusMock requires the stub to be a {@link TokenHelperImpl}; its collaborators go unused as both methods are overridden. */
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
