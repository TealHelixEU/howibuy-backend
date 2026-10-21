package eu.tealhelix.catalogue;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import eu.tealhelix.catalogue.services.v1.impl.IngestionRunDispatcher;
import io.quarkus.runtime.StartupEvent;

/**
 * When the Ingestion starts, the runs that a stop interrupted are processed again, along with those that wait.
 */
@ApplicationScoped
public class IngestionRecovery {
	void onStart(@Observes StartupEvent event, IngestionRunDispatcher dispatcher) {
		dispatcher.recover();
	}
}
