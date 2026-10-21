package eu.tealhelix.catalogue.tests;

import static eu.tealhelix.catalogue.tests.TestFeeds.awaitTheEndOf;
import static eu.tealhelix.catalogue.tests.TestFeeds.feed;
import static eu.tealhelix.catalogue.tests.TestFeeds.product;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import jakarta.inject.Inject;

import eu.tealhelix.catalogue.services.v1.FeedService;
import eu.tealhelix.catalogue.test.quarkus.WithCatalogueDb;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.common.v1.model.Role;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.model.impl.UserImpl;
import eu.tealhelix.common.v1.types.impl.UserIdImpl;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.path.json.JsonPath;
import io.restassured.path.json.config.JsonPathConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The Catalogue Sync reads the Retailer Products that changed after a value of the change sequence, each in its
 * complete current state, page by page. Lime Fresh pushes the Feeds through the service to a {@link TestCatalogue} of
 * each test's own; the products the other tests stamped come before the start of each test, which is read up to first.
 */
@QuarkusTest
@WithCatalogueDb
@TestSecurity(user = "catalogue-sync", roles = "catalogue-sync")
@OidcSecurity(claims = {@Claim(key = "sub", value = ProductChangesTest.CATALOGUE_SYNC), @Claim(key = "client_id", value = "catalogue-sync")})
class ProductChangesTest {
	static final String CATALOGUE_SYNC = "518cae6a-f2b2-4454-b74d-f2404feab2f5";
	private static final User LIME_FRESH = new UserImpl(new UserIdImpl(TestCatalogue.LIME_FRESH), null, null, false, false, Set.of(Role.RETAILER));
	private static final JsonPathConfig EXACT_NUMBERS = new JsonPathConfig(JsonPathConfig.NumberReturnType.BIG_DECIMAL);

	@Inject
	DataSource dataSource;

	@Inject
	FeedService feedService;

	private TestCatalogue catalogue;

	@BeforeEach
	void createTheCatalogueOfTheTest() throws SQLException {
		catalogue = TestCatalogue.create(dataSource);
	}

	@AfterEach
	void removeTheCatalogueOfTheTest() throws SQLException {
		catalogue.remove();
	}

	@Test
	void readsEachChangedProductInItsCompleteState() throws Exception {
		var start = endOfTheChanges();
		process("SNAPSHOT", feed(
				product(catalogue.key("milk"), Map.of("el", "Γάλα", "en", "Milk"), Map.of("fat", "3.5%"), List.of("dairy", "fresh"), "1.29"),
				product(catalogue.key("bread"), Map.of("el", "Ψωμί"), "0.99")
		));

		var page = changesAfter(start, 10);

		assertThat(page.getList("products.productKey")).containsExactly(catalogue.key("milk"), catalogue.key("bread"));
		var stamps = catalogue.changeSequences();
		assertThat(page.getList("products.changeSequence", Long.class)).containsExactly(stamps.get(catalogue.key("milk")), stamps.get(catalogue.key("bread")));
		assertThat(page.getLong("toSequence")).isEqualTo(stamps.get(catalogue.key("bread")));
		assertThat(page.getString("products[0].retailerId")).isEqualTo(TestCatalogue.LIME_FRESH);
		assertThat(page.getBoolean("products[0].removed")).isFalse();
		assertThat(page.getString("products[0].inputHash")).isEqualTo(catalogue.inputHash(catalogue.key("milk")));
		assertThat(page.getMap("products[0].names")).isEqualTo(Map.of("el", "Γάλα", "en", "Milk"));
		assertThat(page.getMap("products[0].characteristics")).isEqualTo(Map.of("fat", "3.5%"));
		assertThat(page.getList("products[0].tags")).containsExactly("dairy", "fresh");
		assertThat(page.getList("products[0].listings")).isEqualTo(List.of(
				Map.of("catalogueId", catalogue.id(), "price", new BigDecimal("1.2900"), "currency", "EUR", "active", true)
		));
	}

	@Test
	void aProductChangedTwiceIsReadOnceAtItsLatestChange() throws Exception {
		var start = endOfTheChanges();
		process("SNAPSHOT", feed(product(catalogue.key("milk"), Map.of("el", "Γάλα"), "1.29"), product(catalogue.key("bread"), Map.of("el", "Ψωμί"), "0.99")));
		process("DELTA", feed(product(catalogue.key("milk"), Map.of("el", "Γάλα"), "1.19")));

		var page = changesAfter(start, 10);

		assertThat(page.getList("products.productKey")).containsExactly(catalogue.key("bread"), catalogue.key("milk"));
		assertThat(page.getList("products[1].listings.price")).containsExactly(new BigDecimal("1.1900"));
	}

	@Test
	void eachPageIsReadAfterThePageBeforeUntilAnEmptyOne() throws Exception {
		var start = endOfTheChanges();
		process("SNAPSHOT", feed(
				product(catalogue.key("milk"), Map.of("el", "Γάλα"), "1.29"),
				product(catalogue.key("bread"), Map.of("el", "Ψωμί"), "0.99"),
				product(catalogue.key("cheese"), Map.of("el", "Τυρί"), "4.50")
		));

		var first = changesAfter(start, 2);
		var second = changesAfter(first.getLong("toSequence"), 2);
		var last = changesAfter(second.getLong("toSequence"), 2);

		assertThat(first.getList("products.productKey")).containsExactly(catalogue.key("milk"), catalogue.key("bread"));
		assertThat(second.getList("products.productKey")).containsExactly(catalogue.key("cheese"));
		assertThat(last.getList("products")).isEmpty();
		assertThat(last.getLong("toSequence")).isEqualTo(second.getLong("toSequence"));
	}

	/**
	 * Products are removed by the eviction, which the test stands in for in the database.
	 */
	@Test
	void aRemovedProductIsReadAsItsIdentityAlone() throws Exception {
		process("SNAPSHOT", feed(product(catalogue.key("milk"), Map.of("el", "Γάλα"), "1.29")));
		var start = endOfTheChanges();
		catalogue.removeProduct(catalogue.key("milk"));

		var page = changesAfter(start, 10);

		assertThat(page.getList("products")).hasSize(1);
		var removed = page.getMap("products[0]");
		assertThat(removed).containsEntry("productKey", catalogue.key("milk")).containsEntry("retailerId", TestCatalogue.LIME_FRESH).containsEntry("removed", true);
		assertThat(page.getLong("products[0].changeSequence")).isEqualTo(catalogue.changeSequences().get(catalogue.key("milk")));
		assertThat(removed.get("inputHash")).isNull();
		assertThat(removed.get("names")).isNull();
		assertThat(removed.get("characteristics")).isNull();
		assertThat(removed.get("tags")).isNull();
		assertThat(removed.get("listings")).isNull();
	}

	@Test
	void readsFromTheFirstChangeWithoutAValueToReadAfter() throws Exception {
		process("SNAPSHOT", feed(product(catalogue.key("milk"), Map.of("el", "Γάλα"), "1.29")));

		var page = given().when().get("/api/catalogue/v1/product-changes?limit=1").then().statusCode(200).extract().jsonPath();

		assertThat(page.getList("products")).hasSize(1);
		assertThat(page.getLong("toSequence")).isEqualTo(page.getLong("products[0].changeSequence"));
	}

	@Test
	void refusesAPageOutOfRange() {
		given().when().get("/api/catalogue/v1/product-changes?after=-1").then().statusCode(400);
		given().when().get("/api/catalogue/v1/product-changes?limit=0").then().statusCode(400);
		given().when().get("/api/catalogue/v1/product-changes?limit=1001").then().statusCode(400);
		given().when().get("/api/catalogue/v1/product-changes?limit=1000").then().statusCode(200);
	}

	@Test
	@TestSecurity(user = "lime_fresh", roles = "retailer")
	@OidcSecurity(claims = {@Claim(key = "sub", value = TestCatalogue.LIME_FRESH), @Claim(key = "client_id", value = "lime_fresh")})
	void aRetailerReadsNoChanges() {
		given().when().get("/api/catalogue/v1/product-changes").then().statusCode(403);
	}

	@Test
	@TestSecurity(user = "operator", roles = "operator")
	@OidcSecurity(claims = @Claim(key = "sub", value = FeedDeactivationTest.OPERATOR))
	void anOperatorReadsNoChanges() {
		given().when().get("/api/catalogue/v1/product-changes").then().statusCode(403);
	}

	/**
	 * Read the changes up to the last one.
	 *
	 * @return The value of the change sequence that the next change will follow
	 */
	private long endOfTheChanges() {
		long after = 0;
		JsonPath page;
		while (!(page = changesAfter(after, 1000)).getList("products").isEmpty()) {
			after = page.getLong("toSequence");
		}
		return after;
	}

	private static JsonPath changesAfter(long after, int limit) {
		return given().when().get("/api/catalogue/v1/product-changes?after=" + after + "&limit=" + limit)
				.then().statusCode(200)
				.extract().jsonPath().using(EXACT_NUMBERS);
	}

	/**
	 * Push a Feed as Lime Fresh and wait until its run is done.
	 */
	private void process(String feedType, String feed) throws SQLException, InterruptedException {
		var run = feedService.receive(LIME_FRESH, new CatalogueIdImpl(catalogue.id()), FeedType.valueOf(feedType), new ByteArrayInputStream(feed.getBytes(UTF_8)));
		assertThat(awaitTheEndOf(dataSource, run.getId().asString())).isEqualTo("DONE");
	}
}
