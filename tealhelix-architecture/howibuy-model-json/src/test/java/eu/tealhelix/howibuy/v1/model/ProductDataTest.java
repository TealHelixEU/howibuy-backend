package eu.tealhelix.howibuy.v1.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.tealhelix.howibuy.v1.json.ObjectMapperModelUtils;
import eu.tealhelix.howibuy.v1.types.ProductKey;
import eu.tealhelix.howibuy.v1.types.impl.ProductKeyImpl;
import org.junit.jupiter.api.Test;

public class ProductDataTest {
	@Test
	void testDeserialization() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		var json = """
				{
					"productKey": "product-key",
					"language": "el",
					"name": "The name",
					"price": 123.45,
					"currency": "EUR",
					"characteristics": {
						"key1": "value1",
						"key2": "value2"
					},
					"tags": ["tag1", "tag2"]
				}
				""";
		var result = om.readValue(json, ProductData.class);
		assertThat(result.getProductKey()).isEqualTo(new ProductKeyImpl("product-key"));
		assertThat(result.getLanguage()).isEqualTo(Locale.of("el"));
		assertThat(result.getName()).isEqualTo("The name");
		assertThat(result.getPrice()).isEqualTo(BigDecimal.valueOf(123.45));
		assertThat(result.getCharacteristics()).isEqualTo(Map.of("key1", "value1", "key2", "value2"));
		assertThat(result.getTags()).isEqualTo(List.of("tag1", "tag2"));
	}

	@Test
	void toLogStringRendersAllFields() {
		var pd = ImmutableProductData.builder()
				.productKey(new ProductKeyImpl("product-key"))
				.language(Locale.of("el"))
				.name("The name")
				.price(new BigDecimal("123.45"))
				.currency(Currency.getInstance("EUR"))
				.putCharacteristics("key1", "value1")
				.putCharacteristics("key2", "value2")
				.addTags("tag1", "tag2")
				.build();
		assertThat(ProductData.toLogString(pd)).isEqualTo(
				"ProductData{\"product-key\", language=el, name=\"The name\", price=123.45, currency=EUR, "
						+ "characteristics={\"key1\"=\"value1\", \"key2\"=\"value2\"}, tags=[\"tag1\", \"tag2\"]}");
	}

	@Test
	void toLogStringReturnsNullForNullArgument() {
		assertThat(ProductData.toLogString(null)).isEqualTo("null");
	}

	@Test
	void toLogStringRendersEmptyCollections() {
		var pd = ImmutableProductData.builder()
				.productKey(new ProductKeyImpl("pk"))
				.language(Locale.of("en"))
				.name("Name")
				.price(new BigDecimal("1.00"))
				.currency(Currency.getInstance("USD"))
				.build();
		assertThat(ProductData.toLogString(pd)).isEqualTo(
				"ProductData{\"pk\", language=en, name=\"Name\", price=1.00, currency=USD, "
						+ "characteristics={}, tags=[]}");
	}

	@Test
	void toLogStringRendersNullFieldsWithoutFailing() {
		var pd = new StubProductData(null, null, null, null, null, null, null);
		assertThat(ProductData.toLogString(pd)).isEqualTo(
				"ProductData{null, language=null, name=null, price=null, currency=null, characteristics=null, tags=null}");
	}

	@Test
	void toLogStringRendersNullCharacteristicValue() {
		var characteristics = new LinkedHashMap<String, String>();
		characteristics.put("key", null);
		var pd = new StubProductData(
				new ProductKeyImpl("pk"), Locale.of("en"), "Name", new BigDecimal("1.00"),
				Currency.getInstance("USD"), characteristics, List.of("tag"));
		assertThat(ProductData.toLogString(pd)).isEqualTo(
				"ProductData{\"pk\", language=en, name=\"Name\", price=1.00, currency=USD, "
						+ "characteristics={\"key\"=null}, tags=[\"tag\"]}");
	}

	/**
	 * A ProductData that permits null fields, which {@link ImmutableProductData} forbids, so that the null-handling
	 * branches of {@link ProductData#toLogString} can be exercised. The components are get-prefixed so their record
	 * accessors satisfy the {@link ProductData} getters directly.
	 */
	private record StubProductData(
			ProductKey getProductKey, Locale getLanguage, String getName, BigDecimal getPrice,
			Currency getCurrency, Map<String, String> getCharacteristics, List<String> getTags)
			implements ProductData {
	}
}
