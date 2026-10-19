package eu.tealhelix.howibuy.tests;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.common.test.quarkus.InjectKeycloak;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * The Catalogue Sync over the whole stack: the IDM authenticates its service account, the application recognizes it by
 * its realm role, and a page of changes for the development Retailer's Catalogue is applied on top of the cursor. The
 * behaviour of applying pages is covered by {@code CatalogueChangesTest}.
 */
@QuarkusTest
@WithRetailerIdm
public class CatalogueSyncWorkflowTest {
	private static final String BASE = "/api/howibuy/v1/catalogue-changes";
	private static final String LIME_FRESH_ID = "dc73176c-7069-4a4f-a74f-d60c4a5ec1ee";

	@InjectKeycloak
	private GenericContainer<?> keycloak;

	@Inject
	DataSource dataSource;

	@BeforeEach
	void setUp() throws SQLException {
		try (var c = dataSource.getConnection(); var s = c.createStatement()) {
			s.execute("DELETE FROM TH_LISTING");
			s.execute("DELETE FROM TH_RETAILER_PRODUCT_NAME");
			s.execute("DELETE FROM TH_RETAILER_PRODUCT");
			s.execute("UPDATE TH_CATALOGUE_CHANGE_CURSOR SET last_sequence = 0");
		}
	}

	@Test
	void theSyncServiceAccountAppliesAPageOnTopOfTheCursor() throws SQLException {
		var token = ServiceAccount.CATALOGUE_SYNC.accessToken(keycloak);
		var product = Map.of(
				"retailerId", LIME_FRESH_ID,
				"productKey", "lime-yoghurt",
				"changeSequence", 7,
				"removed", false,
				"inputHash", "h7",
				"names", Map.of("el", "Γιαούρτι"),
				"characteristics", Map.of(),
				"tags", List.of(),
				"listings", List.of(Map.of("catalogueId", "lime-fresh-gr", "price", new BigDecimal("1.99"), "currency", "EUR", "active", true))
		);

		RestAssured.given()
				.header("Authorization", "Bearer " + token)
				.when().get(BASE + "/cursor")
				.then().statusCode(200).contentType(JSON).body("lastSequence", equalTo(0));
		RestAssured.given()
				.header("Authorization", "Bearer " + token)
				.contentType(JSON)
				.body(Map.of("fromSequence", 0, "toSequence", 7, "products", List.of(product)))
				.when().post(BASE)
				.then().statusCode(204);
		RestAssured.given()
				.header("Authorization", "Bearer " + token)
				.when().get(BASE + "/cursor")
				.then().statusCode(200).body("lastSequence", equalTo(7));

		try (var c = dataSource.getConnection(); var s = c.createStatement(); var rs = s.executeQuery("SELECT count(*) FROM TH_LISTING WHERE catalogue_id = 'lime-fresh-gr'")) {
			rs.next();
			assertEquals(1, rs.getLong(1), "the product is listed in the development Catalogue");
		}
	}

	@Test
	void aRetailerIsRefused() {
		RestAssured.given()
				.header("Authorization", "Bearer " + ServiceAccount.RETAILER.accessToken(keycloak))
				.when().get(BASE + "/cursor")
				.then().statusCode(403);
	}
}
