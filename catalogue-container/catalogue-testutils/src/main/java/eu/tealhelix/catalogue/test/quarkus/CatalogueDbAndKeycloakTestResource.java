package eu.tealhelix.catalogue.test.quarkus;

import java.util.HashMap;
import java.util.Map;

import eu.tealhelix.common.test.quarkus.PostgresAndKeycloakTestResource;

/**
 * Postgres and a real Keycloak, with the Ingestion verifying the tokens of that Keycloak. The other tests of the
 * Ingestion run without an IDM.
 */
public class CatalogueDbAndKeycloakTestResource extends PostgresAndKeycloakTestResource {
	@Override
	public Map<String, String> start() {
		var result = new HashMap<>(super.start());
		result.put("quarkus.oidc.tenant-enabled", "true");
		result.put("quarkus.oidc.auth-server-url", realmUrl());
		return result;
	}
}
