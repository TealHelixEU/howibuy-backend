package eu.tealhelix.catalogue.test.quarkus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;

/**
 * The Ingestion database, seeded from the {@code dev} Liquibase context, and a real Keycloak whose tokens the Ingestion
 * verifies. Every test carrying this annotation is grouped onto a single Quarkus instance, apart from the tests that
 * need no IDM.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WithTestResource(value = CatalogueDbAndKeycloakTestResource.class, initArgs = {
		@ResourceArg(name = "pgContexts", value = "dev")
})
public @interface WithCatalogueDbAndKeycloak {
}
