package eu.tealhelix.howibuy.v1.json;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface ObjectMapperModelUtils {
	static ObjectMapper applyDefaultObjectMapperConfiguration(ObjectMapper om) {
		om.registerModule(new HowiBuyJacksonModule());
		return om;
	}
}
