package eu.tealhelix.howibuy.v1.types;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import eu.tealhelix.howibuy.v1.types.impl.ProductKeyImpl;

public class ProductKeyDeserializer extends JsonDeserializer<ProductKey> {
	@Override
	public ProductKey deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
		return new ProductKeyImpl(jsonParser.getValueAsString());
	}
}
