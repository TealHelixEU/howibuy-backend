package eu.tealhelix.howibuy.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import io.restassured.RestAssured;

/**
 * The user the application answers for a token, read off the greeting page — the simplest thing to ask of a token that
 * is supposed to authenticate someone.
 */
public interface GreetingPage {
	Pattern GREETING = Pattern.compile("<h1>Hello (.*)!</h1>", Pattern.MULTILINE);

	static String userId(String accessToken) {
		var page = RestAssured
				.given()
				.header("Authorization", "Bearer " + accessToken)
				.header("Accepts", "text/html")
				.when()
				.get("/api/howibuy/v1/greeting")
				.then()
				.statusCode(200)
				.extract()
				.body()
				.asString();

		var greeting = GREETING.matcher(page);
		assertTrue(greeting.find(), "the greeting names the user it is for");
		return greeting.group(1);
	}
}
