package eu.tealhelix.howibuy.v1.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.tealhelix.howibuy.v1.json.ObjectMapperModelUtils;
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
}
