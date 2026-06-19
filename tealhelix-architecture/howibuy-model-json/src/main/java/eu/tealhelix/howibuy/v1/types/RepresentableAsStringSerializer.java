package eu.tealhelix.howibuy.v1.types;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import eu.tealhelix.common.types.RepresentableAsString;

public class RepresentableAsStringSerializer extends JsonSerializer<RepresentableAsString> {
	@Override
	public void serialize(RepresentableAsString representableAsString, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
		// Remember: representableAsString is never null, as per the specs of Jackson
		jsonGenerator.writeString(representableAsString.asString());
	}
}
