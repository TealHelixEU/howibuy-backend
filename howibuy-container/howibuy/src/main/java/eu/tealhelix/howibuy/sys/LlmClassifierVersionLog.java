package eu.tealhelix.howibuy.sys;

import static eu.tealhelix.howibuy.services.v1.impl.ClassificationServiceImpl.LLM_VERSION_KEY;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs at startup the classifier version the attempts of the LLM are recorded with, so the logs tell which model,
 * prompt and glossary classified the products.
 */
@ApplicationScoped
public class LlmClassifierVersionLog {
	private static final Logger LOG = LoggerFactory.getLogger(LlmClassifierVersionLog.class);

	void onStart(@Observes StartupEvent event, @ConfigProperty(name = LLM_VERSION_KEY) String llmVersion) {
		LOG.info("The LLM classifies with the classifier version, classifierVersion: {}", llmVersion);
	}
}
