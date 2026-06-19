package eu.tealhelix.howibuy.sys;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.tealhelix.howibuy.v1.json.ObjectMapperModelUtils;
import io.quarkus.jackson.ObjectMapperCustomizer;

@Singleton
public class HowiBuyObjectMapperCustomizer implements ObjectMapperCustomizer {
	@Override
	public void customize(ObjectMapper objectMapper) {
		ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(objectMapper);
	}
}
